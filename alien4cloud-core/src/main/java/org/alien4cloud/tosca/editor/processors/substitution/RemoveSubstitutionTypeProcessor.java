package org.alien4cloud.tosca.editor.processors.substitution;

import javax.annotation.Resource;

import org.alien4cloud.tosca.editor.EditionContextManager;
import org.alien4cloud.tosca.editor.operations.substitution.RemoveSubstitutionTypeOperation;
import org.alien4cloud.tosca.editor.processors.IEditorOperationProcessor;
import org.springframework.stereotype.Component;

import org.alien4cloud.tosca.catalog.index.CsarService;
import alien4cloud.exception.DeleteReferencedObjectException;
import alien4cloud.exception.NotFoundException;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.model.templates.Topology;
import alien4cloud.topology.TopologyService;

/**
 * Delete the substitute of a topology template.
 */
@Component
public class RemoveSubstitutionTypeProcessor implements IEditorOperationProcessor<RemoveSubstitutionTypeOperation> {

    @Resource
    private TopologyService topologyService;

    @Resource
    private CsarService csarService;

    @Override
    public void process(RemoveSubstitutionTypeOperation operation) {
        Topology topology = EditionContextManager.getTopology();

        if (topology.getSubstitutionMapping() == null || topology.getSubstitutionMapping().getSubstitutionType() == null) {
            throw new NotFoundException("No substitution type has been found");
        }

        NodeType substitutionType = topology.getSubstitutionMapping().getSubstitutionType();

        Csar csar = csarService.getTopologySubstitutionCsar(topology.getId());
        if (csar != null) {
            Topology[] topologies = csarService.getDependantTopologies(csar.getName(), csar.getVersion());
            if (topologies != null) {
                for (Topology topologyThatUseCsar : topologies) {
                    if (!topologyThatUseCsar.getId().equals(topology.getId())) {
                        throw new DeleteReferencedObjectException(
                                "The substitution can not be removed since it's type is already used in at least another topology");
                    }
                }
            }
            Csar[] dependantCsars = csarService.getDependantCsars(csar.getName(), csar.getVersion());
            if (dependantCsars != null && dependantCsars.length > 0) {
                throw new DeleteReferencedObjectException("The substitution can not be removed since it's a dependency for another csar");
            }
        }
        topologyService.unloadType(topology, new String[] { substitutionType.getElementId() });
        topology.setSubstitutionMapping(null);
    }
}