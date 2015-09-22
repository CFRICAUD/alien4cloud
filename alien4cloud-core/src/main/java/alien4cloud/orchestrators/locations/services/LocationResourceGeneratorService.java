package alien4cloud.orchestrators.locations.services;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import alien4cloud.exception.NotFoundException;
import alien4cloud.model.components.CSARDependency;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.model.topology.Capability;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import alien4cloud.topology.TopologyServiceCore;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

/**
 * Location Resource Generator Service provides utilities to generate location resources .
 */
@Component
@Slf4j
public class LocationResourceGeneratorService {
    @Inject
    private TopologyServiceCore topologyService;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageFlavorContext {
        private List<LocationResourceTemplate> templates;
        private String idPropertyName;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComputeContext {
        private List<IndexedNodeType> nodeTypes = Lists.newArrayList();
        private String generatedNamePrefix = "Compute";
        private String imageIdPropertyName;
        private String flavorIdPropertyName;

    }

    /**
     * Generate resources of type compute given a set of images and flavors
     *
     * @param imageContext
     * @param flavorContext
     * @param computeContext
     * @param resourceAccessor
     * @return
     */
    public List<LocationResourceTemplate> generateComputeFromImageAndFlavor(ImageFlavorContext imageContext, ImageFlavorContext flavorContext,
            ComputeContext computeContext, ILocationResourceAccessor resourceAccessor) {
        List<LocationResourceTemplate> images = imageContext.getTemplates();
        List<LocationResourceTemplate> flavors = flavorContext.getTemplates();
        Set<CSARDependency> dependencies = resourceAccessor.getDependencies();

        List<LocationResourceTemplate> generated = Lists.newArrayList();

        int count = 0;
        for (LocationResourceTemplate image : images) {
            for (LocationResourceTemplate flavor : flavors) {
                for (IndexedNodeType indexedNodeType : computeContext.getNodeTypes()) {
                    String namePrefix = StringUtils.isNotBlank(computeContext.getGeneratedNamePrefix()) ? computeContext.getGeneratedNamePrefix()
                            : "GeneratedCompute";
                    NodeTemplate node = topologyService.buildNodeTemplate(dependencies, indexedNodeType, null);
                    // set the imageId
                    node.getProperties()
                            .put(computeContext.getImageIdPropertyName(), image.getTemplate().getProperties().get(imageContext.getIdPropertyName()));
                    // set the flavorId
                    node.getProperties().put(computeContext.getFlavorIdPropertyName(),
                            flavor.getTemplate().getProperties().get(flavorContext.getIdPropertyName()));

                    // copy os and host capabilities properties
                    copyCapabilityBasedOnTheType(image.getTemplate(), node, "os");
                    copyCapabilityBasedOnTheType(flavor.getTemplate(), node, "host");

                    LocationResourceTemplate resource = new LocationResourceTemplate();
                    resource.setService(false);
                    resource.setTemplate(node);
                    resource.setName(namePrefix + count);
                    count++;

                    generated.add(resource);
                }
            }
        }

        return generated;
    }

    /**
     * Copy a capability based on its type from a node to another
     *
     * @param from
     * @param to
     * @param capabilityName
     */
    private void copyCapabilityBasedOnTheType(NodeTemplate from, NodeTemplate to, String capabilityName) {
        if (MapUtils.isEmpty(from.getCapabilities()) || MapUtils.isEmpty(to.getCapabilities())) {
            return;
        }
        Capability capa = to.getCapabilities().get(capabilityName);
        if (capa != null) {
            // copy the first found and exit
            for (Capability capaToCheck : from.getCapabilities().values()) {
                if (Objects.equal(capaToCheck.getType(), capa.getType())) {
                    to.getCapabilities().put(capabilityName, capaToCheck);
                    return;
                }
            }
        }
    }

    public ImageFlavorContext buildContext(String elementType, String idPropertyName, ILocationResourceAccessor resourceAccessor) {
        return new ImageFlavorContext(resourceAccessor.getResources(elementType), idPropertyName);
    }

    public ComputeContext buildComputeContext(String elementType, String namePefix, String imageIdPropertyName, String flavorIdPropertyName,
            ILocationResourceAccessor resourceAccessor) {
        ComputeContext context = new ComputeContext();
        context.setFlavorIdPropertyName(flavorIdPropertyName);
        context.setImageIdPropertyName(imageIdPropertyName);
        context.setGeneratedNamePrefix(namePefix);
        try {
            IndexedNodeType nodeType = resourceAccessor.getIndexedToscaElement(elementType);
            context.getNodeTypes().add(nodeType);
        } catch (NotFoundException e) {
            log.warn("No compute found with the element id: " + elementType, e);
        }
        return context;
    }
}
