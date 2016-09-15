package org.alien4cloud.tosca.editor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.alien4cloud.tosca.editor.operations.AbstractEditorOperation;
import org.alien4cloud.tosca.editor.operations.UpdateFileOperation;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.Topology;
import org.springframework.stereotype.Component;

import com.google.common.cache.*;

import alien4cloud.component.repository.IFileRepository;
import alien4cloud.csar.services.CsarService;
import alien4cloud.topology.TopologyService;
import alien4cloud.topology.TopologyServiceCore;
import alien4cloud.tosca.context.ToscaContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * The topology edition context manager is responsible to manage the caching and lifecycle of TopologyEditionContexts.
 */
@Slf4j
@Component
public class EditionContextManager {
    /** Holds the topology context */
    private final static ThreadLocal<EditionContext> contextThreadLocal = new ThreadLocal<>();

    @Inject
    private CsarService csarService;
    @Inject
    private TopologyServiceCore topologyServiceCore;
    @Inject
    private TopologyService topologyService;
    @Inject
    private EditorRepositoryService repositoryService;
    @Inject
    private IFileRepository artifactRepository;

    // TODO make cache management time a parameter
    private LoadingCache<String, EditionContext> contextCache;

    @PostConstruct
    public void setup() {
        // initialize the cache
        contextCache = CacheBuilder.newBuilder().expireAfterAccess(60, TimeUnit.MINUTES).removalListener(new RemovalListener<String, EditionContext>() {
            @Override
            public void onRemoval(RemovalNotification<String, EditionContext> removalNotification) {
                log.debug("Topology edition context with id {} has been evicted. {} pending operations are lost.", removalNotification.getKey(),
                        removalNotification.getValue().getOperations().size());
                for (AbstractEditorOperation operation : removalNotification.getValue().getOperations()) {
                    if (operation instanceof UpdateFileOperation) {
                        String fileId = ((UpdateFileOperation) operation).getTempFileId();
                        if (artifactRepository.isFileExist(fileId)) {
                            artifactRepository.deleteFile(fileId);
                        }
                    }
                }
            }
        }).build(new CacheLoader<String, EditionContext>() {
            @Override
            public EditionContext load(String csarId) throws Exception {
                log.debug("Loading edition context for archive {}", csarId);
                Csar csar = csarService.getOrFail(csarId);
                Topology topology = topologyServiceCore.getOrFail(csarId);
                // check if the topology git repository has been created already
                // FIXME we should use directly the folder from the archive repository
                Path topologyGitPath = repositoryService.createGitDirectory(csarId);
                if (csar.getYamlFilePath() == null) {
                    csar.setYamlFilePath("topology.yml");
                    // export the content of the topology in the yaml.
                    Path targetPath = topologyGitPath.resolve(csar.getYamlFilePath());
                    String yaml = topologyService.getYaml(csar, topology);
                    try (BufferedWriter writer = Files.newBufferedWriter(targetPath)) {
                        writer.write(yaml);
                    }
                }
                log.debug("Edition context for archive {} loaded", csar);
                return new EditionContext(csar, topology, topologyGitPath);
            }
        });
    }

    /**
     * Initialize thread local contexts for the topology.
     * 
     * @param topologyId The id of the topology.
     */
    @SneakyThrows
    public synchronized void init(String topologyId) {
        contextThreadLocal.set(contextCache.get(topologyId));
        ToscaContext.set(contextThreadLocal.get().getToscaContext());
    }

    /**
     * Reset the state of the topology context to it's initial state.
     * 
     * @throws IOException In case the parsing of the directory content fails.
     */
    public void reset() throws IOException {
        Topology topology = topologyServiceCore.getOrFail(getTopology().getId());
        contextThreadLocal.get().reset(topology);
    }

    /**
     * Get the current topology edition context for the thread.
     * 
     * @return The thread's topology edition context.
     */
    public static EditionContext get() {
        return contextThreadLocal.get();
    }

    /**
     * Get the current topology under edition.
     *
     * @return The thread's topology under edition.
     */
    public static Csar getCsar() {
        return contextThreadLocal.get().getCsar();
    }

    /**
     * Get the current topology under edition.
     *
     * @return The thread's topology under edition.
     */
    public static Topology getTopology() {
        return contextThreadLocal.get().getTopology();
    }

    /**
     * Remove thread local contexts.
     */
    public void destroy() {
        contextThreadLocal.remove();
        ToscaContext.destroy();
    }
}