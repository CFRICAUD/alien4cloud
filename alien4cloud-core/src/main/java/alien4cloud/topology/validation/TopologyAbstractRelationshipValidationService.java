package alien4cloud.topology.validation;

import alien4cloud.component.CSARRepositorySearchService;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.components.IndexedInheritableToscaElement;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.topology.task.TaskCode;
import alien4cloud.topology.task.TopologyTask;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Lists;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Performs validation by checking that no relationships in a topology are abstract (and cannot be instanciated).
 */
@Component
public class TopologyAbstractRelationshipValidationService {
    @Resource
    private CSARRepositorySearchService csarRepoSearchService;

    /**
     * Checks that no relationships in a topology are abstract (and cannot be instanciated).
     *
     * @param topology The topology to validate.
     * @return A list tasks to be done to make this topology valid.
     */
    public List<TopologyTask> validateAbstractRelationships(Topology topology) {
        Map<String, IndexedRelationshipType[]> abstractIndexedRelationshipTypes = getIndexedRelationshipTypesFromTopology(topology, true);
        return getTaskListFromMapArray(abstractIndexedRelationshipTypes, TaskCode.IMPLEMENT);
    }

    /**
     * Get the relationships from a topology
     *
     * @param topology topology to be validated
     * @param abstractOnes if only abstract ones should be retrieved
     * @return a map containing node template id --> list of relationship type that this node references
     */
    private Map<String, IndexedRelationshipType[]> getIndexedRelationshipTypesFromTopology(Topology topology, Boolean abstractOnes) {
        Map<String, IndexedRelationshipType[]> indexedRelationshipTypesMap = Maps.newHashMap();
        if (topology.getNodeTemplates() == null) {
            return indexedRelationshipTypesMap;
        }
        for (Map.Entry<String, NodeTemplate> template : topology.getNodeTemplates().entrySet()) {
            if (template.getValue().getRelationships() == null) {
                continue;
            }

            Set<IndexedRelationshipType> indexedRelationshipTypes = Sets.newHashSet();
            for (RelationshipTemplate relTemplate : template.getValue().getRelationships().values()) {
                IndexedRelationshipType indexedRelationshipType = csarRepoSearchService.getElementInDependencies(IndexedRelationshipType.class,
                        relTemplate.getType(), topology.getDependencies());
                if (indexedRelationshipType != null) {
                    if (abstractOnes == null || abstractOnes.equals(indexedRelationshipType.isAbstract())) {
                        indexedRelationshipTypes.add(indexedRelationshipType);
                    }
                } else {
                    throw new NotFoundException("Relationship Type [" + relTemplate.getType() + "] cannot be found");
                }
            }
            if (indexedRelationshipTypes.size() > 0) {
                indexedRelationshipTypesMap.put(template.getKey(),
                        indexedRelationshipTypes.toArray(new IndexedRelationshipType[indexedRelationshipTypes.size()]));
            }

        }
        return indexedRelationshipTypesMap;
    }

    /**
     * Constructs a TopologyTask list given a Map (node template name => component) and the code
     */
    private <T extends IndexedInheritableToscaElement> List<TopologyTask> getTaskListFromMapArray(Map<String, T[]> components, TaskCode taskCode) {
        List<TopologyTask> taskList = Lists.newArrayList();
        for (Map.Entry<String, T[]> entry : components.entrySet()) {
            for (IndexedInheritableToscaElement compo : entry.getValue()) {
                TopologyTask task = new TopologyTask();
                task.setNodeTemplateName(entry.getKey());
                task.setComponent(compo);
                task.setCode(taskCode);
                taskList.add(task);
            }
        }
        if (taskList.isEmpty()) {
            return null;
        } else {
            return taskList;
        }
    }
}
