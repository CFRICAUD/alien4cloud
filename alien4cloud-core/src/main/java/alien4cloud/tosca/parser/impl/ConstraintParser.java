package alien4cloud.tosca.parser.impl;

import java.util.Map;

import javax.annotation.Resource;

import lombok.AllArgsConstructor;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;

import alien4cloud.tosca.container.model.type.PropertyConstraint;
import alien4cloud.tosca.parser.AbstractTypeNodeParser;
import alien4cloud.tosca.parser.INodeParser;
import alien4cloud.tosca.parser.MappingTarget;
import alien4cloud.tosca.parser.ParserUtils;
import alien4cloud.tosca.parser.ParsingContextExecution;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingTechnicalException;
import alien4cloud.tosca.parser.ParsingError;
import alien4cloud.tosca.properties.constraints.EqualConstraint;
import alien4cloud.tosca.properties.constraints.GreaterOrEqualConstraint;
import alien4cloud.tosca.properties.constraints.GreaterThanConstraint;
import alien4cloud.tosca.properties.constraints.InRangeConstraint;
import alien4cloud.tosca.properties.constraints.LengthConstraint;
import alien4cloud.tosca.properties.constraints.LessOrEqualConstraint;
import alien4cloud.tosca.properties.constraints.LessThanConstraint;
import alien4cloud.tosca.properties.constraints.MaxLengthConstraint;
import alien4cloud.tosca.properties.constraints.MinLengthConstraint;
import alien4cloud.tosca.properties.constraints.PatternConstraint;
import alien4cloud.tosca.properties.constraints.ValidValuesConstraint;

import com.google.common.collect.Maps;

/**
 * Parse a constraint based on the specified operator
 */
@Component
public class ConstraintParser extends AbstractTypeNodeParser implements INodeParser<PropertyConstraint> {
    @Resource
    private ScalarParser scalarParser;
    private Map<String, ConstraintParsingInfo> constraintBuildersMap;

    public ConstraintParser() {
        constraintBuildersMap = Maps.newHashMap();
        constraintBuildersMap.put("equal", new ConstraintParsingInfo(EqualConstraint.class, "equal"));
        constraintBuildersMap.put("greater_than", new ConstraintParsingInfo(GreaterThanConstraint.class, "greaterThan"));
        constraintBuildersMap.put("greater_or_equal", new ConstraintParsingInfo(GreaterOrEqualConstraint.class, "greaterOrEqual"));
        constraintBuildersMap.put("less_than", new ConstraintParsingInfo(LessThanConstraint.class, "lessThan"));
        constraintBuildersMap.put("less_or_equal", new ConstraintParsingInfo(LessOrEqualConstraint.class, "lessOrEqual"));
        constraintBuildersMap.put("in_range", new ConstraintParsingInfo(InRangeConstraint.class, "inRange"));
        constraintBuildersMap.put("valid_values", new ConstraintParsingInfo(ValidValuesConstraint.class, "validValues"));
        constraintBuildersMap.put("length", new ConstraintParsingInfo(LengthConstraint.class, "length"));
        constraintBuildersMap.put("min_length", new ConstraintParsingInfo(MinLengthConstraint.class, "minLength"));
        constraintBuildersMap.put("max_length", new ConstraintParsingInfo(MaxLengthConstraint.class, "maxLength"));
        constraintBuildersMap.put("pattern", new ConstraintParsingInfo(PatternConstraint.class, "pattern"));
    }

    @Override
    public boolean isDeffered() {
        return false;
    }

    @Override
    public PropertyConstraint parse(Node node, ParsingContextExecution context) {
        if (node instanceof MappingNode) {
            MappingNode mappingNode = (MappingNode) node;
            if (mappingNode.getValue().size() == 1) {
                NodeTuple nodeTuple = mappingNode.getValue().get(0);
                String operator = ParserUtils.getScalar(nodeTuple.getKeyNode(), context.getParsingErrors());
                // based on the operator we should load the right constraint.
                return parseConstraint(operator, nodeTuple.getKeyNode(), nodeTuple.getValueNode(), context);
            } else {
                ParserUtils.addTypeError(node, context.getParsingErrors(), "Constraint");
            }
        } else {
            ParserUtils.addTypeError(node, context.getParsingErrors(), "Constraint");
        }
        return null;
    }

    private PropertyConstraint parseConstraint(String operator, Node keyNode, Node expressionNode, ParsingContextExecution context) {
        ConstraintParsingInfo info = constraintBuildersMap.get(operator);
        if (info == null) {
            context.getParsingErrors().add(
                    new ParsingError(ParsingErrorLevel.WARNING, "Constraint parsing issue", keyNode.getStartMark(),
                            "Unknown constraint operator, will be ignored.", keyNode.getEndMark(), operator));
            return null;
        }
        PropertyConstraint constraint;
        try {
            constraint = info.constraintClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new ParsingTechnicalException("Unable to create constraint.", e);
        }
        BeanWrapper target = new BeanWrapperImpl(constraint);
        parseAndSetValue(target, null, expressionNode, context, new MappingTarget(info.propertyName, scalarParser));
        return constraint;
    }

    @AllArgsConstructor
    private class ConstraintParsingInfo {
        private Class<? extends PropertyConstraint> constraintClass;
        private String propertyName;
    }
}