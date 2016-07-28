package org.alien4cloud.tosca.editor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.alien4cloud.tosca.editor.exception.EditionConcurrencyException;
import org.alien4cloud.tosca.editor.exception.EditorIOException;
import org.alien4cloud.tosca.editor.operations.AbstractEditorOperation;
import org.alien4cloud.tosca.editor.processors.IEditorCommitableProcessor;
import org.alien4cloud.tosca.editor.processors.IEditorOperationProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import alien4cloud.exception.NotFoundException;
import alien4cloud.git.SimpleGitHistoryEntry;
import alien4cloud.model.topology.Topology;
import alien4cloud.security.AuthorizationUtil;
import alien4cloud.topology.TopologyDTO;
import alien4cloud.topology.TopologyService;
import alien4cloud.topology.TopologyServiceCore;
import alien4cloud.utils.CollectionUtils;
import alien4cloud.utils.ReflectionUtil;

/**
 * This service manages command execution on the TOSCA topology template editor.
 */
@Service
public class EditorService {
    @Inject
    private ApplicationContext applicationContext;
    @Inject
    private TopologyService topologyService;
    @Inject
    private TopologyServiceCore topologyServiceCore;
    @Inject
    private EditionContextManager editionContextManager;
    @Inject
    private TopologyDTOBuilder dtoBuilder;
    @Inject
    private EditorRepositoryService repositoryService;

    /** Processors map by type. */
    private Map<Class<?>, IEditorOperationProcessor<? extends AbstractEditorOperation>> processorMap = Maps.newHashMap();

    @PostConstruct
    public void initialize() {
        Map<String, IEditorOperationProcessor> processors = applicationContext.getBeansOfType(IEditorOperationProcessor.class);
        for (IEditorOperationProcessor processor : processors.values()) {
            Class<?> operationClass = ReflectionUtil.getGenericArgumentType(processor.getClass(), IEditorOperationProcessor.class, 0);
            processorMap.put(operationClass, processor);
        }
    }

    /**
     * Check the authorization in the context of a topology edition.
     * 
     * @param topologyId The id of the topology.
     */
    public void checkAuthorization(String topologyId) {
        try {
            editionContextManager.init(topologyId);
            topologyService.checkEditionAuthorizations(EditionContextManager.getTopology());
        } finally {
            editionContextManager.destroy();
        }
    }

    /**
     * Call this method only for checking optimistic locking and initializing edition context for method that don't process an operation (save, undo etc.)
     * 
     * @param topologyId The id of the topology under edition.
     * @param lastOperationId The id of the last operation.
     */
    private void initContext(String topologyId, String lastOperationId) {
        // create a fake operation for optimistic locking
        AbstractEditorOperation optimisticLockOperation = new AbstractEditorOperation() {
            @Override
            public String commitMessage() {
                return "This operation will never be enqueued and never commited.";
            }
        };
        optimisticLockOperation.setPreviousOperationId(lastOperationId);
        // init the edition context with the fake operation.
        initContext(topologyId, optimisticLockOperation);
    }

    /**
     * Initialize the edition context, (checks authorizations etc.)
     * 
     * @param topologyId The id of the topology under edition.
     * @param operation The operation to be processed.
     */
    private void initContext(String topologyId, AbstractEditorOperation operation) {
        editionContextManager.init(topologyId);
        // check authorization to update a topology
        topologyService.checkEditionAuthorizations(EditionContextManager.getTopology());
        // If the version of the topology is not snapshot we don't allow modifications.
        topologyService.throwsErrorIfReleased(EditionContextManager.getTopology());
        // check that operations can be executed (based on a kind of optimistic locking
        checkSynchronization(operation);
    }

    /**
     * Ensure that the request is synchronized with the current state of the edition.
     *
     * @param operation, The operation under evaluation.
     */
    private synchronized void checkSynchronization(AbstractEditorOperation operation) {
        // there is an operation being processed so just fail (nobody could get the notification)
        if (EditionContextManager.get().getCurrentOperation() != null) {
            throw new EditionConcurrencyException();
        }
        List<AbstractEditorOperation> operations = EditionContextManager.get().getOperations();
        // if someone performed some operations we have to ensure that the new operation is performed on top of a synchronized topology
        if (EditionContextManager.get().getLastOperationIndex() == -1) {
            if (operation.getPreviousOperationId() != null) {
                throw new EditionConcurrencyException();
            }
        } else if (!operation.getPreviousOperationId().equals(operations.get(EditionContextManager.get().getLastOperationIndex()).getId())) {
            throw new EditionConcurrencyException();
        }
        operation.setId(UUID.randomUUID().toString());
        EditionContextManager.get().setCurrentOperation(operation);
        return;
    }

    // trigger editor operation
    @MessageMapping("/topology-editor/{topologyId}")
    public <T extends AbstractEditorOperation> TopologyDTO execute(@DestinationVariable String topologyId, T operation) {
        // get the topology context.
        try {
            initContext(topologyId, operation);
            operation.setAuthor(AuthorizationUtil.getCurrentUser().getUserId());

            // attach the topology tosca context and process the operation
            IEditorOperationProcessor<T> processor = (IEditorOperationProcessor<T>) processorMap.get(operation.getClass());
            processor.process(operation);

            List<AbstractEditorOperation> operations = EditionContextManager.get().getOperations();
            if (EditionContextManager.get().getLastOperationIndex() == operations.size() - 1) {
                // Clear the operations to 'redo'.
                CollectionUtils.clearFrom(operations, EditionContextManager.get().getLastOperationIndex() + 1);
            }
            // update the last operation and index
            EditionContextManager.get().getOperations().add(operation);
            EditionContextManager.get().setLastOperationIndex(EditionContextManager.get().getOperations().size() - 1);

            // return the topology context
            return dtoBuilder.buildTopologyDTO(EditionContextManager.get());
        } finally {
            EditionContextManager.get().setCurrentOperation(null);
            editionContextManager.destroy();
        }
    }

    /**
     * Undo or redo operations until the given index (including)
     * 
     * @param topologyId The id of the topology for which to undo or redo operations.
     * @param at The index on which to place the undo/redo cursor (-1 means no operations, then 0 is first operation etc.)
     * @param lastOperationId The last known operation id for client optimistic locking.
     * @return The topology DTO.
     */
    public TopologyDTO undoRedo(String topologyId, int at, String lastOperationId) {
        try {
            initContext(topologyId, lastOperationId);

            if (-1 > at || at > EditionContextManager.get().getOperations().size()) {
                throw new NotFoundException("Unable to find the requested index for undo/redo");
            }

            if (at == EditionContextManager.get().getLastOperationIndex()) {
                // nothing to change.
                return dtoBuilder.buildTopologyDTO(EditionContextManager.get());
            }

            // TODO Improve this by avoiding dao query for (deep) cloning topology and keeping cache for TOSCA types that are required.
            editionContextManager.reset();

            for (int i = 0; i < at + 1; i++) {
                AbstractEditorOperation operation = EditionContextManager.get().getOperations().get(i);
                IEditorOperationProcessor processor = processorMap.get(operation.getClass());
                processor.process(operation);
            }

            EditionContextManager.get().setLastOperationIndex(at);

            return dtoBuilder.buildTopologyDTO(EditionContextManager.get());
        } catch (IOException e) {
            // FIXME undo should be fail-safe...
            return null;
        } finally {
            EditionContextManager.get().setCurrentOperation(null);
            editionContextManager.destroy();
        }
    }

    /**
     * Save a topology under edition. It updates the local repository files, the topology in elastic-search and perform a local git commit.
     * 
     * @param topologyId The id of the topology under edition.
     * @param lastOperationId The id of the last operation.
     */
    public TopologyDTO save(String topologyId, String lastOperationId) {
        try {
            initContext(topologyId, lastOperationId);

            EditionContext context = EditionContextManager.get();
            if (context.getLastOperationIndex() <= context.getLastSavedOperationIndex()) {
                // nothing to save..
                return dtoBuilder.buildTopologyDTO(EditionContextManager.get());
            }

            StringBuilder commitMessage = new StringBuilder();
            // copy and cleanup all temporary files from the executed operations.
            for (int i = context.getLastSavedOperationIndex() + 1; i <= context.getLastOperationIndex(); i++) {
                AbstractEditorOperation operation = context.getOperations().get(i);
                IEditorOperationProcessor<?> processor = (IEditorOperationProcessor) processorMap.get(operation.getClass());
                if (processor instanceof IEditorCommitableProcessor) {
                    ((IEditorCommitableProcessor) processor).beforeCommit(operation);
                }
                commitMessage.append(operation.getAuthor()).append(": ").append(operation.commitMessage()).append("\n");
            }

            saveYamlFile();

            Topology topology = EditionContextManager.getTopology();
            // Save the topology in elastic search
            topologyServiceCore.save(topology);
            topologyServiceCore.updateSubstitutionType(topology);

            // Local git commit
            repositoryService.commit(topologyId, commitMessage.toString());

            // TODO add support for undo even after save, this require ability to rollback files to git state, we need file rollback support for that..
            context.setOperations(Lists.newArrayList(context.getOperations().subList(context.getLastOperationIndex() + 1, context.getOperations().size())));
            context.setLastOperationIndex(-1);

            return dtoBuilder.buildTopologyDTO(EditionContextManager.get());
        } catch (IOException e) {
            // when there is a failure in file copy to the local repo.
            // FIXME git revert to put back the local files state in the initial state.
            throw new EditorIOException("Error while saving files state in local repository", e);
        } finally {
            EditionContextManager.get().setCurrentOperation(null);
            editionContextManager.destroy();
        }
    }

    private void saveYamlFile() throws IOException {
        Topology topology = EditionContextManager.getTopology();
        Path targetPath = EditionContextManager.get().getLocalGitPath().resolve(topology.getYamlFilePath());
        String yaml = topologyService.getYaml(topology);
        try (BufferedWriter writer = Files.newBufferedWriter(targetPath)) {
            writer.write(yaml);
        }
    }

    /**
     * Performs a git pull.
     */
    public void pull() {
        // pull can be done only if there is no unsaved commands

        // This operation just fails in case of conflicts

        // The topology is updated

    }

    /**
     * Push the content to a remote git repository.
     *
     * Note that conflicts are not managed in a4c. In case of conflicts a new branch is created for manual merge by users.
     */
    public void push() {

    }

    /**
     * Retrieve simplified vision of the git history for the given topology.
     * 
     * @param topologyId The id of the topology.
     * @param from from which index to get history.
     * @param count number of histories entry to retrieve.
     * @return a list of simplified git commit entry.
     */
    public List<SimpleGitHistoryEntry> history(String topologyId, int from, int count) {
        return repositoryService.getHistory(topologyId, from, count);
    }
}