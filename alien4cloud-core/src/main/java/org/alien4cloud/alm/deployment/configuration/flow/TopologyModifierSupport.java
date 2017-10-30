package org.alien4cloud.alm.deployment.configuration.flow;

import alien4cloud.application.TopologyCompositionService;
import alien4cloud.model.common.Tag;
import alien4cloud.tosca.context.ToscaContext;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.alien4cloud.tosca.editor.operations.nodetemplate.AddNodeOperation;
import org.alien4cloud.tosca.editor.operations.nodetemplate.ReplaceNodeOperation;
import org.alien4cloud.tosca.editor.operations.nodetemplate.UpdateNodePropertyValueOperation;
import org.alien4cloud.tosca.editor.operations.relationshiptemplate.AddRelationshipOperation;
import org.alien4cloud.tosca.editor.processors.nodetemplate.AddNodeProcessor;
import org.alien4cloud.tosca.editor.processors.nodetemplate.ReplaceNodeProcessor;
import org.alien4cloud.tosca.editor.processors.nodetemplate.UpdateNodePropertyValueProcessor;
import org.alien4cloud.tosca.editor.processors.relationshiptemplate.AddRelationshipProcessor;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ComplexPropertyValue;
import org.alien4cloud.tosca.model.definitions.ListPropertyValue;
import org.alien4cloud.tosca.model.templates.*;
import org.alien4cloud.tosca.model.types.RelationshipType;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static alien4cloud.utils.AlienUtils.safe;

/**
 * Base class for topology modifiers that can helps adding nodes, setting properties, replacing nodes, adding relationships.
 */
public abstract class TopologyModifierSupport implements ITopologyModifier {

    @Resource
    protected AddNodeProcessor addNodeProcessor;

    @Resource
    protected ReplaceNodeProcessor replaceNodeProcessor;

    @Resource
    protected AddRelationshipProcessor addRelationshipProcessor;

    @Resource
    protected UpdateNodePropertyValueProcessor updateNodePropertyValueProcessor;

    /**
     * Add a node template in the topology.
     *
     * @param csar
     * @param topology
     * @param desiredNodeName the name you would like for this node (but can be suffixed if this name is already used).
     * @param nodeType
     * @param nodeVersion
     * @return the created node.
     */
    // TODO ALIEN-2589: unit test
    protected NodeTemplate addNodeTemplate(Csar csar, Topology topology, String desiredNodeName, String nodeType, String nodeVersion) {
        AddNodeOperation addNodeOperation = new AddNodeOperation();
        String nodeName = TopologyCompositionService.ensureNodeNameIsUnique(topology.getNodeTemplates().keySet(), desiredNodeName, 0);
        addNodeOperation.setNodeName(nodeName);
        addNodeOperation.setIndexedNodeTypeId(nodeType + ":" + nodeVersion);
        addNodeProcessor.process(csar, topology, addNodeOperation);
        return topology.getNodeTemplates().get(nodeName);
    }

    protected RelationshipTemplate addRelationshipTemplate(Csar csar, Topology topology, NodeTemplate sourceNode, String targetNodeName, String relationshipTypeName, String requirementName, String capabilityName) {
        AddRelationshipOperation addRelationshipOperation = new AddRelationshipOperation();
        addRelationshipOperation.setNodeName(sourceNode.getName());
        addRelationshipOperation.setTarget(targetNodeName);
        RelationshipType relationshipType = ToscaContext.get(RelationshipType.class, relationshipTypeName);
        addRelationshipOperation.setRelationshipType(relationshipType.getElementId());
        addRelationshipOperation.setRelationshipVersion(relationshipType.getArchiveVersion());
        addRelationshipOperation.setRequirementName(requirementName);
        addRelationshipOperation.setTargetedCapabilityName(capabilityName);
        String relationShipName = TopologyCompositionService.ensureNodeNameIsUnique(safe(sourceNode.getRelationships()).keySet(),
                sourceNode.getName() + "_" + targetNodeName, 0);
        addRelationshipOperation.setRelationshipName(relationShipName);
        addRelationshipProcessor.process(csar, topology, addRelationshipOperation);
        return sourceNode.getRelationships().get(relationShipName);
    }

    protected NodeTemplate replaceNode(Csar csar, Topology topology, NodeTemplate node, String nodeType, String nodeVersion) {
        ReplaceNodeOperation replaceNodeOperation = new ReplaceNodeOperation();
        replaceNodeOperation.setNodeName(node.getName());
        replaceNodeOperation.setNewTypeId(nodeType + ":" + nodeVersion);
        replaceNodeProcessor.process(csar, topology, replaceNodeOperation);
        return topology.getNodeTemplates().get(node.getName());
    }

    /**
     * Add the propertyValue to the list at the given path (Only the last property of the path must be a list).
     */
    protected void appendNodePropertyPathValue(Csar csar, Topology topology, NodeTemplate nodeTemplate, String propertyPath, AbstractPropertyValue propertyValue) {
        setNodePropertyPathValue(csar, topology, nodeTemplate, propertyPath, propertyValue, true);
    }

    /**
     * Set the propertyValue at the given path (doesn't manage lists in the path).
     */
    protected void setNodePropertyPathValue(Csar csar, Topology topology, NodeTemplate nodeTemplate, String propertyPath, AbstractPropertyValue propertyValue) {
        setNodePropertyPathValue(csar, topology, nodeTemplate, propertyPath, propertyValue, false);
    }

    /**
     * Change policies that target the sourceTemplate and make them target the targetTemplate.
     */
    public static void changePolicyTarget(Topology topology, NodeTemplate sourceTemplate, NodeTemplate targetTemplate) {
        Set<PolicyTemplate> policies = TopologyNavigationUtil.getTargetedPolicies(topology, sourceTemplate);
        policies.forEach(policyTemplate -> {
            policyTemplate.getTargets().remove(sourceTemplate.getName());
            policyTemplate.getTargets().add(targetTemplate.getName());
        });
    }

    private void setNodePropertyPathValue(Csar csar, Topology topology, NodeTemplate nodeTemplate, String propertyPath, AbstractPropertyValue propertyValue, boolean lastPropertyIsAList) {
        Map<String, AbstractPropertyValue> propertyValues = nodeTemplate.getProperties();
        String nodePropertyName = feedPropertyValue(propertyValues, propertyPath, propertyValue, lastPropertyIsAList);
        Object nodePropertyValue = propertyValues.get(nodePropertyName);

        UpdateNodePropertyValueOperation updateNodePropertyValueOperation = new UpdateNodePropertyValueOperation();
        updateNodePropertyValueOperation.setNodeName(nodeTemplate.getName());
        updateNodePropertyValueOperation.setPropertyName(nodePropertyName);
        // TODO: can be necessary to serialize value before setting it in case of different types
        updateNodePropertyValueOperation.setPropertyValue(nodePropertyValue);
        updateNodePropertyValueProcessor.process(csar, topology, updateNodePropertyValueOperation);
    }

    protected String feedPropertyValue(Map propertyValues, String propertyPath, Object propertyValue, boolean lastPropertyIsAList) {
        String nodePropertyName = null;
        if (propertyPath.contains(".")) {
            String[] paths = propertyPath.split("\\.");
            nodePropertyName = paths[0];
            Map<String, Object> currentMap = null;
            for (int i = 0; i < paths.length; i++) {
                if (i == 0) {
                    Object currentPropertyValue = propertyValues.get(paths[i]);
                    if (currentPropertyValue != null && currentPropertyValue instanceof ComplexPropertyValue) {
                        currentMap = ((ComplexPropertyValue) currentPropertyValue).getValue();
                    } else {
                        // FIXME OVERRIDING PROP VALUE This overrides the nodePropertyName property value!!!. We should instead fail if currentPropertyValue not
                        // instanceof ComplexPropertyValue
                        // FIXME and do this only if currentPropertyValue is null
                        currentMap = Maps.newHashMap();
                        propertyValues.put(nodePropertyName, new ComplexPropertyValue(currentMap));
                    }
                } else if (i == paths.length - 1) {
                    // TODO: find a better way to manage this
                    if (lastPropertyIsAList) {
                        Object currentEntry = currentMap.get(paths[i]);
                        ListPropertyValue listPropertyValue = null;
                        if (currentEntry != null && currentEntry instanceof ListPropertyValue) {
                            listPropertyValue = (ListPropertyValue) currentEntry;
                        } else {
                            // FIXME Same as OVERRIDING PROP VALUE above
                            listPropertyValue = new ListPropertyValue(Lists.newArrayList());
                            currentMap.put(paths[i], listPropertyValue);
                        }
                        listPropertyValue.getValue().add(propertyValue);
                    } else {
                        currentMap.put(paths[i], propertyValue);
                    }
                } else {
                    Map<String, Object> currentPropertyValue = null;
                    Object currentPropertyValueObj = currentMap.get(paths[i]);
                    if (currentPropertyValueObj != null && currentPropertyValueObj instanceof Map<?, ?>) {
                        currentPropertyValue = (Map<String, Object>) currentPropertyValueObj;
                    } else {
                        // FIXME Same as OVERRIDING PROP VALUE above
                        currentPropertyValue = Maps.newHashMap();
                        currentMap.put(paths[i], currentPropertyValue);
                    }
                    currentMap = currentPropertyValue;
                }
            }
        } else {
            nodePropertyName = propertyPath;
            propertyValues.put(nodePropertyName, propertyValue);
        }
        return nodePropertyName;
    }

    protected void setNodeTagValue(AbstractTemplate template, String name, String value) {
        List<Tag> tags = template.getTags();
        if (tags == null) {
            tags = Lists.newArrayList();
            template.setTags(tags);
        }
        tags.add(new Tag(name, value));
    }

}
