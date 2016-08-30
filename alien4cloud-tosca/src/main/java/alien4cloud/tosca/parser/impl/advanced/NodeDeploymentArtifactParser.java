package alien4cloud.tosca.parser.impl.advanced;

import org.springframework.stereotype.Component;

@Component
public class NodeDeploymentArtifactParser extends DeploymentArtifactParser {

    public NodeDeploymentArtifactParser() {
        super(ArtifactReferenceMissingMode.RAISE_WARNING);
    }
}
