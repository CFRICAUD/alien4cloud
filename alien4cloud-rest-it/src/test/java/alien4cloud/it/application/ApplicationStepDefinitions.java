package alien4cloud.it.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alien4cloud.tosca.editor.operations.nodetemplate.AddNodeOperation;
import org.elasticsearch.common.collect.Maps;
import org.junit.Assert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import alien4cloud.dao.model.FacetedSearchResult;
import alien4cloud.dao.model.GetMultipleDataResult;
import alien4cloud.it.Context;
import alien4cloud.it.common.CommonStepDefinitions;
import alien4cloud.it.security.AuthenticationStepDefinitions;
import alien4cloud.it.topology.EditorStepDefinitions;
import alien4cloud.it.topology.TopologyStepDefinitions;
import alien4cloud.model.application.Application;
import alien4cloud.model.application.ApplicationEnvironment;
import alien4cloud.model.application.ApplicationVersion;
import alien4cloud.model.application.EnvironmentType;
import alien4cloud.model.common.Tag;
import alien4cloud.rest.application.model.ApplicationEnvironmentDTO;
import alien4cloud.rest.application.model.ApplicationEnvironmentRequest;
import alien4cloud.rest.application.model.CreateApplicationRequest;
import alien4cloud.rest.application.model.UpdateApplicationEnvironmentRequest;
import alien4cloud.rest.component.SearchRequest;
import alien4cloud.rest.component.UpdateTagRequest;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.rest.utils.JsonUtil;
import alien4cloud.utils.ReflectionUtil;
import cucumber.api.DataTable;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApplicationStepDefinitions {
    private static final String TEST_APPLICATION_IMAGE = "src/test/resources/data/test-image.png";
    public static Application CURRENT_APPLICATION;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    public static Map<String, Application> CURRENT_APPLICATIONS = Maps.newHashMap(); // | APP_NAME -> APP_OBJECT |

    private AuthenticationStepDefinitions authSteps = new AuthenticationStepDefinitions();
    private CommonStepDefinitions commonStepDefinitions = new CommonStepDefinitions();
    private TopologyStepDefinitions topoSteps = new TopologyStepDefinitions();

    private void setAppVersionIdToContext(String appId) throws IOException {
        String applicationVersionJson = Context.getRestClientInstance().get("/rest/v1/applications/" + appId + "/versions");
        RestResponse<ApplicationVersion> appVersion = JsonUtil.read(applicationVersionJson, ApplicationVersion.class);
        Context.getInstance().registerApplicationVersionId(appVersion.getData().getVersion(), appVersion.getData().getId());
    }

    @SuppressWarnings("rawtypes")
    public void setAppEnvironmentIdToContext(String applicationName) throws IOException {
        String applicationId = Context.getInstance().getApplicationId(applicationName);
        SearchRequest request = new SearchRequest();
        request.setFrom(0);
        request.setSize(10);
        String applicationEnvironmentsJson = Context.getRestClientInstance().postJSon("/rest/v1/applications/" + applicationId + "/environments/search",
                JsonUtil.toString(request));
        RestResponse<GetMultipleDataResult> restResponse = JsonUtil.read(applicationEnvironmentsJson, GetMultipleDataResult.class);
        GetMultipleDataResult searchResp = restResponse.getData();
        ApplicationEnvironmentDTO appEnvDTO = JsonUtil.readObject(JsonUtil.toString(searchResp.getData()[0]), ApplicationEnvironmentDTO.class);
        Context.getInstance().registerApplicationEnvironmentId(applicationName, appEnvDTO.getName(), appEnvDTO.getId());
    }

    @When("^I create a new application with name \"([^\"]*)\" and description \"([^\"]*)\"$")
    public void I_create_a_new_application_with_name_and_description(String name, String description) throws Throwable {
        CreateApplicationRequest request = new CreateApplicationRequest(name, description, null);
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().postJSon("/rest/v1/applications/", JsonUtil.toString(request)));
        RestResponse<String> reponse = JsonUtil.read(Context.getInstance().getRestResponse(), String.class);
        String applicationJson = Context.getRestClientInstance().get("/rest/v1/applications/" + reponse.getData());
        RestResponse<Application> application = JsonUtil.read(applicationJson, Application.class);
        CURRENT_APPLICATION = (application.getData() == null) ? new Application() : application.getData();
        CURRENT_APPLICATIONS.put(name, application.getData());

        Context.getInstance().registerApplication(CURRENT_APPLICATION);
        Context.getInstance().registerApplicationId(CURRENT_APPLICATION.getName(), CURRENT_APPLICATION.getId());
        if (application.getData() != null) {
            String appId = application.getData().getId();
            setAppVersionIdToContext(appId);
            setAppEnvironmentIdToContext(application.getData().getName());
            String topologyId = getTopologyIdFromApplication(application.getData().getName());
            Context.getInstance().registerTopologyId(topologyId);
            Context.getInstance().registerApplicationId(name, appId);
        }
    }

    private String getTopologyIdFromApplication(String name) throws IOException {
        String response = Context.getRestClientInstance().get("/rest/v1/applications/" + Context.getInstance().getApplicationId(name) + "/environments/"
                + Context.getInstance().getDefaultApplicationEnvironmentId(name) + "/topology");
        return JsonUtil.read(response, String.class).getData();
    }

    @Then("^The RestResponse should contain an id string$")
    public void The_RestResponse_should_contain_an_id_string() throws Throwable {
        String response = Context.getInstance().getRestResponse();
        assertNotNull(response);
        RestResponse<String> restResponse = JsonUtil.read(response, String.class);
        assertNotNull(restResponse);
        assertNotNull(restResponse.getData());
        assertEquals(String.class, restResponse.getData().getClass());
    }

    private void createApplicationFromTemplateId(String name, String description, String templateId) throws Throwable {
        // create the application linked to this template
        CreateApplicationRequest request = new CreateApplicationRequest(name, description, templateId);
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().postJSon("/rest/v1/applications/", JsonUtil.toString(request)));

        // check the created application (topologyId)
        RestResponse<String> response = JsonUtil.read(Context.getInstance().getRestResponse(), String.class);
        String applicationJson = Context.getRestClientInstance().get("/rest/v1/applications/" + response.getData());
        Application application = JsonUtil.read(applicationJson, Application.class).getData();
        assertNotNull(application);
        CURRENT_APPLICATION = application;
        CURRENT_APPLICATIONS.put(name, application);
        Context.getInstance().registerApplication(application);
        Context.getInstance().registerApplicationId(name, application.getId());
        setAppEnvironmentIdToContext(application.getName());
        setAppVersionIdToContext(application.getId());
        String topologyId = getTopologyIdFromApplication(application.getName());
        assertNotNull(topologyId);
        Context.getInstance().registerTopologyId(topologyId);
    }

    @When("^I create a new application with name \"([^\"]*)\" and description \"([^\"]*)\" without errors$")
    public void I_create_a_new_application_with_name_and_description_without_errors(String name, String description) throws Throwable {
        I_create_a_new_application_with_name_and_description(name, description);
        commonStepDefinitions.I_should_receive_a_RestResponse_with_no_error();
    }

    @When("^I retrieve the newly created application$")
    public void I_retrieve_the_newly_created_application() throws Throwable {
        // App from context
        Application contextApp = Context.getInstance().getApplication();
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().get("/rest/v1/applications/" + contextApp.getId()));
    }

    @Given("^There is a \"([^\"]*)\" application$")
    public void There_is_a_application(String applicationName) throws Throwable {
        SearchRequest searchRequest = new SearchRequest(null, applicationName, 0, 50, null);
        String searchResponse = Context.getRestClientInstance().postJSon("/rest/v1/applications/search", JsonUtil.toString(searchRequest));
        RestResponse<FacetedSearchResult> response = JsonUtil.read(searchResponse, FacetedSearchResult.class);
        boolean hasApplication = false;
        for (Object appAsObj : response.getData().getData()) {
            Application app = JsonUtil.readObject(JsonUtil.toString(appAsObj), Application.class);
            if (applicationName.equals(app.getName())) {
                hasApplication = true;
                CURRENT_APPLICATION = app;
            }
        }
        if (!hasApplication) {
            I_create_a_new_application_with_name_and_description(applicationName, "");
        }
    }

    @When("^I add a tag with key \"([^\"]*)\" and value \"([^\"]*)\" to the application$")
    public void I_add_a_tag_with_key_and_value_to_the_component(String key, String value) throws Throwable {
        addTag(CURRENT_APPLICATION.getId(), key, value);
    }

    private void addTag(String applicationId, String key, String value) throws JsonProcessingException, IOException {
        UpdateTagRequest updateTagRequest = new UpdateTagRequest();
        updateTagRequest.setTagKey(key);
        updateTagRequest.setTagValue(value);
        Context.getInstance().registerRestResponse(
                Context.getRestClientInstance().postJSon("/rest/v1/applications/" + applicationId + "/tags", jsonMapper.writeValueAsString(updateTagRequest)));

    }

    @Given("^There is a \"([^\"]*)\" application with tags:$")
    public void There_is_a_application_with_tags(String applicationName, DataTable tags) throws Throwable {
        // Create a new application with tags
        CreateApplicationRequest request = new CreateApplicationRequest(applicationName, "", null);
        String responseAsJson = Context.getRestClientInstance().postJSon("/rest/v1/applications/", JsonUtil.toString(request));
        String applicationId = JsonUtil.read(responseAsJson, String.class).getData();
        Context.getInstance().registerApplicationId(applicationName, applicationId);
        // Add tags to the application
        for (List<String> rows : tags.raw()) {
            addTag(applicationId, rows.get(0), rows.get(1));
        }
        setAppEnvironmentIdToContext(applicationName);
        Context.getInstance().registerRestResponse(responseAsJson);
    }

    @Given("^I have an application tag \"([^\"]*)\"$")
    public boolean I_have_and_a_tag(String tag) throws Throwable {
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().get("/rest/v1/applications/" + CURRENT_APPLICATION.getId()));
        Application application = JsonUtil.read(Context.getInstance().takeRestResponse(), Application.class).getData();
        assertTrue(application.getTags().contains(new Tag(tag, null)));
        return application.getTags().contains(new Tag(tag, null));
    }

    @When("^I delete an application tag with key \"([^\"]*)\"$")
    public void I_delete_a_tag_with_key(String tagId) throws Throwable {
        Context.getInstance()
                .registerRestResponse(Context.getRestClientInstance().delete("/rest/v1/applications/" + CURRENT_APPLICATION.getId() + "/tags/" + tagId));
    }

    private Set<String> registeredApps = Sets.newHashSet();
    private String previousRestResponse;

    @Given("^There is (\\d+) applications indexed in ALIEN$")
    public void There_is_applications_indexed_in_ALIEN(int applicationCount) throws Throwable {
        CURRENT_APPLICATIONS.clear();
        registeredApps.clear();
        for (int i = 0; i < applicationCount; i++) {
            String appName = "name" + i;
            I_create_a_new_application_with_name_and_description(appName, "");
            registeredApps.add(appName);
            CURRENT_APPLICATIONS.put(appName, CURRENT_APPLICATION);
        }
    }

    @When("^I search applications from (\\d+) with result size of (\\d+)$")
    public void I_search_applications_from_with_result_size_of(int from, int to) throws Throwable {
        SearchRequest searchRequest = new SearchRequest(null, "", from, to, null);
        previousRestResponse = Context.getInstance().getRestResponse();
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().postJSon("/rest/v1/applications/search", JsonUtil.toString(searchRequest)));
    }

    @Then("^The RestResponse must contain (\\d+) applications.$")
    public void The_RestResponse_must_contain_applications(int count) throws Throwable {
        RestResponse<FacetedSearchResult> response = JsonUtil.read(Context.getInstance().getRestResponse(), FacetedSearchResult.class);
        assertEquals(count, response.getData().getTotalResults());
        assertEquals(count, response.getData().getData().length);
    }

    @Then("^I should be able to view the (\\d+) other applications.$")
    public void I_should_be_able_to_view_the_other_applications(int count) throws Throwable {

        removeFromRegisteredApps(previousRestResponse);
        removeFromRegisteredApps(Context.getInstance().getRestResponse());

        assertEquals(0, registeredApps.size());
    }

    @SuppressWarnings("unchecked")
    private void removeFromRegisteredApps(String responseAsJson) throws Throwable {
        RestResponse<FacetedSearchResult> response = JsonUtil.read(responseAsJson, FacetedSearchResult.class);
        for (Object appAsObj : response.getData().getData()) {
            Map<String, Object> appAsMap = (Map<String, Object>) appAsObj;
            registeredApps.remove(appAsMap.get("name"));
        }
    }

    @When("^i update its image$")
    public void i_update_its_image() throws Throwable {
        String appId = JsonUtil.read(Context.getInstance().getRestResponse(), String.class).getData();
        RestResponse<String> response = JsonUtil.read(Context.getRestClientInstance().postMultipart("/rest/v1/applications/" + appId + "/image", "file",
                Files.newInputStream(Paths.get(TEST_APPLICATION_IMAGE))), String.class);
        assertNull(response.getError());
    }

    @Then("^the application can be found in ALIEN$")
    public Application the_application_can_be_found_in_ALIEN() throws Throwable {
        String appId = CURRENT_APPLICATION.getId();
        RestResponse<Application> response = JsonUtil.read(Context.getRestClientInstance().get("/rest/v1/applications/" + appId), Application.class);
        assertNotNull(response.getData());
        return response.getData();
    }

    @Then("^the application can be found in ALIEN with its new image$")
    public void the_application_can_be_found_in_ALIEN_with_its_new_image() throws Throwable {
        Application app = the_application_can_be_found_in_ALIEN();
        assertNotNull(app.getImageId());
    }

    @Given("^I create a new application with name \"([^\"]*)\" and description \"([^\"]*)\" and node templates$")
    public void I_create_a_new_application_with_name_and_description_and_node_templates(String applicationName, String applicationDescription,
            DataTable nodeTemplates) throws Throwable {
        // create the topology
        I_create_a_new_application_with_name_and_description(applicationName, applicationDescription);

        EditorStepDefinitions.do_i_get_the_current_topology();

        // add all specified node template to a specific topology (from Application or Topology template)
        for (List<String> row : nodeTemplates.raw()) {
            Map<String, String> operationMap = Maps.newHashMap();
            operationMap.put("type", AddNodeOperation.class.getName());
            operationMap.put("nodeName", row.get(0));
            operationMap.put("indexedNodeTypeId", row.get(1));

            EditorStepDefinitions.do_i_execute_the_operation(operationMap);
        }
        // Save the topology
        EditorStepDefinitions.do_i_save_the_topology();
    }

    @When("^I add a role \"([^\"]*)\" to user \"([^\"]*)\" on the application \"([^\"]*)\"$")
    public void I_add_a_role_to_user_on_the_application(String role, String username, String applicationName) throws Throwable {
        I_search_for_application(applicationName);
        Context.getInstance().registerRestResponse(
                Context.getRestClientInstance().put("/rest/v1/applications/" + CURRENT_APPLICATION.getId() + "/roles/users/" + username + "/" + role));
    }

    @When("^I search for \"([^\"]*)\" application$")
    public void I_search_for_application(String applicationName) throws Throwable {
        SearchRequest searchRequest = new SearchRequest(null, applicationName, 0, 10, null);
        String searchResponse = Context.getRestClientInstance().postJSon("/rest/v1/applications/search", JsonUtil.toString(searchRequest));
        Context.getInstance().registerRestResponse(searchResponse);
        RestResponse<FacetedSearchResult> response = JsonUtil.read(searchResponse, FacetedSearchResult.class);
        for (Object appAsObj : response.getData().getData()) {
            Application app = JsonUtil.readObject(JsonUtil.toString(appAsObj), Application.class);
            if (applicationName.equals(app.getName())) {
                CURRENT_APPLICATION = app;
            }
        }
    }

    @Then("^The application should have a user \"([^\"]*)\" having \"([^\"]*)\" role$")
    public void The_application_should_have_a_user_having_role(String username, String expectedRole) throws Throwable {
        assertNotNull(CURRENT_APPLICATION);
        assertNotNull(CURRENT_APPLICATION.getUserRoles());
        Set<String> userRoles = CURRENT_APPLICATION.getUserRoles().get(username);
        assertNotNull(userRoles);
        assertTrue(userRoles.contains(expectedRole));

    }

    @Given("^there is a user \"([^\"]*)\" with the \"([^\"]*)\" role on the application \"([^\"]*)\"$")
    public void there_is_a_user_with_the_role_on_the_application(String username, String expectedRole, String applicationName) throws Throwable {
        authSteps.There_is_a_user_in_the_system(username);
        I_search_for_application(applicationName);
        Map<String, Set<String>> userRoles = CURRENT_APPLICATION.getUserRoles();
        if (userRoles != null && userRoles.containsKey(username)) {
            if (userRoles.get(username) != null && userRoles.get(username).contains(expectedRole)) {
                return;
            }
        }

        I_add_a_role_to_user_on_the_application(expectedRole, username, applicationName);
    }

    @Given("^there is a user \"([^\"]*)\" with the following roles on the application \"([^\"]*)\"$")
    public void there_is_a_user_with_the_following_roles_on_the_application(String username, String applicationName, List<String> expectedRoles)
            throws Throwable {

        for (String expectedRole : expectedRoles) {
            there_is_a_user_with_the_role_on_the_application(username, expectedRole, applicationName);
        }
    }

    @When("^I remove a role \"([^\"]*)\" to user \"([^\"]*)\" on the application \"([^\"]*)\"$")
    public void I_remove_a_role_to_user_on_the_application(String role, String username, String applicationName) throws Throwable {
        I_search_for_application(applicationName);
        Context.getInstance().registerRestResponse(
                Context.getRestClientInstance().delete("/rest/v1/applications/" + CURRENT_APPLICATION.getId() + "/roles/users/" + username + "/" + role));
    }

    @Then("^The application should have a user \"([^\"]*)\" not having \"([^\"]*)\" role$")
    public void The_application_should_have_a_user_not_having_role(String username, String expectedRole) throws Throwable {
        if (CURRENT_APPLICATION != null && CURRENT_APPLICATION.getUserRoles() != null) {
            Set<String> userRoles = CURRENT_APPLICATION.getUserRoles().get(username);
            assertNotNull(userRoles);
            if (userRoles != null) {
                assertFalse(userRoles.contains(expectedRole));
            }
        }
    }

    @When("^I delete the application \"([^\"]*)\"$")
    public void I_delete_the_application(String applicationName) throws Throwable {
        String id = CURRENT_APPLICATION.getName().equals(applicationName) ? CURRENT_APPLICATION.getId() : CURRENT_APPLICATIONS.get(applicationName).getId();
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().delete("/rest/v1/applications/" + id));
    }

    @Then("^the application should not be found$")
    public Application the_application_should_not_be_found() throws Throwable {
        RestResponse<Application> response = JsonUtil.read(Context.getRestClientInstance().get("/rest/v1/applications/" + CURRENT_APPLICATION.getId()),
                Application.class);
        assertNull(response.getData());
        return response.getData();
    }

    @Given("^I have applications with names and descriptions$")
    public void I_have_applications_with_names_and_description(DataTable applicationNames) throws Throwable {
        CURRENT_APPLICATIONS.clear();
        // Create each application and store in CURRENT_APPS
        for (List<String> app : applicationNames.raw()) {
            CreateApplicationRequest request = new CreateApplicationRequest(app.get(0), app.get(1), null);
            Context.getInstance().registerRestResponse(Context.getRestClientInstance().postJSon("/rest/v1/applications/", JsonUtil.toString(request)));
            RestResponse<String> reponse = JsonUtil.read(Context.getInstance().getRestResponse(), String.class);
            String applicationJson = Context.getRestClientInstance().get("/rest/v1/applications/" + reponse.getData());
            RestResponse<Application> application = JsonUtil.read(applicationJson, Application.class);
            CURRENT_APPLICATIONS.put(app.get(0), application.getData());
            Context.getInstance().registerApplicationId(application.getData().getName(), application.getData().getId());
            setAppEnvironmentIdToContext(application.getData().getName());
        }

        assertEquals(CURRENT_APPLICATIONS.size(), applicationNames.raw().size());

    }

    @Given("^I have applications with names and descriptions and a topology containing a nodeTemplate \"([^\"]*)\" related to \"([^\"]*)\"$")
    public void I_have_applications_with_names_and_description_containing_nodetemplate(String nodeName, String componentType,
            Map<String, String> applicationRequests) throws Throwable {
        CURRENT_APPLICATIONS.clear();

        // Prepare a cucumber data table using the node infos.
        List<String> nodeData = Lists.newArrayList(nodeName, componentType);
        List<List<String>> raw = Lists.newArrayList();
        raw.add(nodeData);
        DataTable dataTable = DataTable.create(raw);

        // Create each application and store in CURRENT_APPS
        for (java.util.Map.Entry<String, String> request : applicationRequests.entrySet()) {
            I_create_a_new_application_with_name_and_description_and_node_templates(request.getKey(), request.getValue(), dataTable);
            CURRENT_APPLICATIONS.put(request.getKey(), CURRENT_APPLICATION);
        }

        assertEquals(CURRENT_APPLICATIONS.size(), applicationRequests.size());
    }

    @Given("^I add a role \"([^\"]*)\" to group \"([^\"]*)\" on the application \"([^\"]*)\"$")
    public void I_add_a_role_to_group_on_the_application(String role, String groupName, String applicationName) throws Throwable {
        I_search_for_application(applicationName);
        Context.getInstance().registerRestResponse(Context.getRestClientInstance()
                .put("/rest/v1/applications/" + CURRENT_APPLICATION.getId() + "/roles/groups/" + Context.getInstance().getGroupId(groupName) + "/" + role));
    }

    @And("^The application should have a group \"([^\"]*)\" having \"([^\"]*)\" role$")
    public void The_application_should_have_a_group_having_role(String groupName, String role) throws Throwable {
        assertNotNull(CURRENT_APPLICATION);
        assertNotNull(CURRENT_APPLICATION.getGroupRoles());
        Set<String> groupRoles = CURRENT_APPLICATION.getGroupRoles().get(Context.getInstance().getGroupId(groupName));
        assertNotNull(groupRoles);
        assertTrue(groupRoles.contains(role));
    }

    @And("^There is a group \"([^\"]*)\" with the following roles on the application \"([^\"]*)\"$")
    public void There_is_a_group_with_the_following_roles_on_the_application(String groupName, String applicationName, List<String> expectedRoles)
            throws Throwable {
        for (String expectedRole : expectedRoles) {
            I_add_a_role_to_group_on_the_application(expectedRole, groupName, applicationName);
        }
    }

    @When("^I remove a role \"([^\"]*)\" from group \"([^\"]*)\" on the application \"([^\"]*)\"$")
    public void I_remove_a_role_from_group_on_the_application(String role, String groupName, String applicationName) throws Throwable {
        I_search_for_application(applicationName);
        Context.getInstance().registerRestResponse(Context.getRestClientInstance()
                .delete("/rest/v1/applications/" + CURRENT_APPLICATION.getId() + "/roles/groups/" + Context.getInstance().getGroupId(groupName) + "/" + role));
    }

    @And("^The application should have the group \"([^\"]*)\" not having \"([^\"]*)\" role$")
    public void The_application_should_have_the_group_not_having_role(String groupName, String role) throws Throwable {
        if (CURRENT_APPLICATION.getGroupRoles() != null) {
            Set<String> groupRoles = CURRENT_APPLICATION.getGroupRoles().get(groupName);
            if (groupRoles != null) {
                assertFalse(groupRoles.contains(role));
            }
        }
    }

    @And("^The application should have the group \"([^\"]*)\" having \"([^\"]*)\" role$")
    public void The_application_should_have_the_group_having_role(String groupName, String role) throws Throwable {
        assertNotNull(CURRENT_APPLICATION.getGroupRoles());
        Set<String> groupRoles = CURRENT_APPLICATION.getGroupRoles().get(Context.getInstance().getGroupId(groupName));
        assertNotNull(groupRoles);
        assertTrue(groupRoles.contains(role));
    }

    @And("^The RestResponse must contain these applications$")
    public void The_RestResponse_must_contain_these_applications(List<String> expectedApplications) throws Throwable {
        RestResponse<FacetedSearchResult> response = JsonUtil.read(Context.getInstance().getRestResponse(), FacetedSearchResult.class);
        assertNotNull(response.getData());
        assertEquals(expectedApplications.size(), response.getData().getTotalResults());
        assertEquals(expectedApplications.size(), response.getData().getData().length);
        Set<String> actualApplications = Sets.newHashSet();
        for (Object appObj : response.getData().getData()) {
            actualApplications.add(((Map) appObj).get("name").toString());
        }
        assertEquals(Sets.newHashSet(expectedApplications), actualApplications);
    }

    @Then("^I should receive an application without \"([^\"]*)\" as user$")
    public void I_should_receive_an_application_without_as_user(String userName) throws Throwable {
        RestResponse<Application> response = JsonUtil.read(Context.getInstance().getRestResponse(), Application.class);
        Assert.assertNull(response.getError());
        Assert.assertNotNull(response.getData());
        Application application = response.getData();
        if (application.getUserRoles() != null) {
            Assert.assertFalse(application.getUserRoles().containsKey(userName));
        }
    }

    @When("^I set the \"([^\"]*)\" of this application to \"([^\"]*)\"$")
    public void I_set_the_of_this_application_to(String fieldName, String fieldValue) throws Throwable {
        Map<String, Object> request = Maps.newHashMap();
        request.put(fieldName, fieldValue);
        Context.getInstance().registerRestResponse(
                Context.getRestClientInstance().putJSon("/rest/v1/applications/" + CURRENT_APPLICATION.getId(), JsonUtil.toString(request)));
        ReflectionUtil.setPropertyValue(CURRENT_APPLICATION, fieldName, fieldValue);
    }

    @And("^The application can be found in ALIEN with its \"([^\"]*)\" set to \"([^\"]*)\"$")
    public void The_application_can_be_found_in_ALIEN_with_its_set_to(String fieldName, String fieldValue) throws Throwable {
        Application application = the_application_can_be_found_in_ALIEN();
        Assert.assertEquals(fieldValue, ReflectionUtil.getPropertyValue(application, fieldName).toString());
    }

    @When("^I create an application environment of type \"([^\"]*)\" with name \"([^\"]*)\" and description \"([^\"]*)\" for the newly created application$")
    public void I_create_an_application_environment_of_type_with_name_and_description_for_the_newly_created_application(String appEnvType, String appEnvName,
            String appEnvDescription) throws Throwable {
        Assert.assertNotNull(CURRENT_APPLICATION.getId());
        Assert.assertTrue(EnvironmentType.valueOf(appEnvType).toString().equals(appEnvType));
        Assert.assertNotNull(appEnvName);

        ApplicationEnvironmentRequest appEnvRequest = new ApplicationEnvironmentRequest();
        appEnvRequest.setEnvironmentType(EnvironmentType.valueOf(appEnvType));
        appEnvRequest.setName(appEnvName);
        appEnvRequest.setDescription(appEnvDescription);
        appEnvRequest.setVersionId(Context.getInstance().getApplicationVersionId("0.1.0-SNAPSHOT"));
        Context.getInstance().registerRestResponse(Context.getRestClientInstance()
                .postJSon("/rest/v1/applications/" + CURRENT_APPLICATION.getId() + "/environments", JsonUtil.toString(appEnvRequest)));
        RestResponse<String> appEnvId = JsonUtil.read(Context.getInstance().getRestResponse(), String.class);
        if (appEnvId.getData() != null) {
            Context.getInstance().registerApplicationEnvironmentId(CURRENT_APPLICATION.getName(), appEnvName, appEnvId.getData());
        }
    }

    @When("^I get the application environment named \"([^\"]*)\"$")
    public void I_get_the_application_environment_named(String applicationEnvironmentName) throws Throwable {
        Assert.assertNotNull(CURRENT_APPLICATION);
        String applicationEnvId = Context.getInstance().getApplicationEnvironmentId(CURRENT_APPLICATION.getName(), applicationEnvironmentName);
        Context.getInstance().registerRestResponse(
                Context.getRestClientInstance().get("/rest/v1/applications/" + CURRENT_APPLICATION.getId() + "/environments/" + applicationEnvId));
        RestResponse<ApplicationEnvironment> appEnvironment = JsonUtil.read(Context.getInstance().getRestResponse(), ApplicationEnvironment.class);
        Assert.assertNotNull(appEnvironment.getData());
        Assert.assertEquals(appEnvironment.getData().getId(), applicationEnvId);
    }

    @When("^I update the application environment named \"([^\"]*)\" with values$")
    public void I_update_the_application_environment_named_with_values(String applicationEnvironmentName, DataTable appEnvAttributeValues) throws Throwable {
        UpdateApplicationEnvironmentRequest appEnvRequest = new UpdateApplicationEnvironmentRequest();
        String attribute = null, attributeValue = null;
        for (List<String> attributesToUpdate : appEnvAttributeValues.raw()) {
            attribute = attributesToUpdate.get(0);
            attributeValue = attributesToUpdate.get(1);
            switch (attribute) {
            case "name":
                appEnvRequest.setName(attributeValue);
                break;
            case "description":
                appEnvRequest.setDescription(attributeValue);
                break;
            case "environmentType":
                appEnvRequest.setEnvironmentType(EnvironmentType.valueOf(attributeValue));
                break;
            default:
                log.info("Attribute <{}> not found in ApplicationEnvironmentRequest object", attribute);
                break;
            }
        }
        // send the update request
        Context.getInstance()
                .registerRestResponse(Context.getRestClientInstance().putJSon(
                        "/rest/v1/applications/" + CURRENT_APPLICATION.getId() + "/environments/"
                                + Context.getInstance().getApplicationEnvironmentId(CURRENT_APPLICATION.getName(), applicationEnvironmentName),
                        JsonUtil.toString(appEnvRequest)));
    }

    @When("^I update the environment named \"([^\"]*)\" to use cloud \"([^\"]*)\" for application \"([^\"]*)\"$")
    public void I_update_the_environment_named_to_use_cloud_for_application(String envName, String cloudName, String appName) throws Throwable {
        UpdateApplicationEnvironmentRequest appEnvRequest = new UpdateApplicationEnvironmentRequest();
        // appEnvRequest.setCloudId(Context.getInstance().getCloudId(cloudName));
        Assert.fail("Fix test");
        String applicationId = Context.getInstance().getApplicationId(appName);
        String applicationEnvironmentId = Context.getInstance().getApplicationEnvironmentId(appName, envName);
        // send the update request
        Context.getInstance().registerRestResponse(Context.getRestClientInstance()
                .putJSon("/rest/v1/applications/" + applicationId + "/environments/" + applicationEnvironmentId, JsonUtil.toString(appEnvRequest)));
    }

    @When("^I delete the registered application environment named \"([^\"]*)\" from its id$")
    public void I_delete_the_registered_application_environment_named_from_its_id(String applicationEnvironmentName) throws Throwable {
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().delete("/rest/v1/applications/" + CURRENT_APPLICATION.getId()
                + "/environments/" + Context.getInstance().getApplicationEnvironmentId(CURRENT_APPLICATION.getName(), applicationEnvironmentName)));
        RestResponse<Boolean> appEnvironment = JsonUtil.read(Context.getInstance().getRestResponse(), Boolean.class);
        Assert.assertNotNull(appEnvironment.getData());
    }

    @Given("^I must have an environment named \"([^\"]*)\" for application \"([^\"]*)\"$")
    public void I_must_have_an_environment_named_for_application(String envName, String appName) throws Throwable {
        Assert.assertNotNull(envName);
        Assert.assertNotNull(appName);
        String environmentId = Context.getInstance().getApplicationEnvironmentId(appName, envName);
        Assert.assertNotNull(environmentId);
    }

    @Then("^The application update date has changed$")
    public void The_application_update_date_has_changed() throws Throwable {
        Application application = CURRENT_APPLICATION;
        Assert.assertNotEquals(application.getCreationDate(), application.getLastUpdateDate());
    }
}
