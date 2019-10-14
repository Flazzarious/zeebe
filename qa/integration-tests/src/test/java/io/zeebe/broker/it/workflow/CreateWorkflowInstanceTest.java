/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.workflow;

import static io.zeebe.broker.it.util.StatusCodeMatcher.hasStatusCode;
import static io.zeebe.broker.it.util.StatusDescriptionMatcher.descriptionContains;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCreated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.grpc.Status.Code;
import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.command.ClientStatusException;
import io.zeebe.client.api.response.DeploymentEvent;
import io.zeebe.client.api.response.Workflow;
import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceWithResultsResponse;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.WorkflowInstanceCreationRecordValue;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.test.util.record.WorkflowInstances;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CreateWorkflowInstanceTest {
  private static EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  private static GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public ExpectedException exception = ExpectedException.none();

  private DeploymentEvent firstDeployment;
  private DeploymentEvent secondDeployment;
  private String processId;

  @Before
  public void deployProcess() {
    processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();

    firstDeployment =
        clientRule
            .getClient()
            .newDeployCommand()
            .addWorkflowModel(process, "workflow.bpmn")
            .send()
            .join();

    secondDeployment =
        clientRule
            .getClient()
            .newDeployCommand()
            .addWorkflowModel(process, "workflow.bpmn")
            .send()
            .join();

    clientRule.waitUntilDeploymentIsDone(secondDeployment.getKey());
  }

  @Test
  public void shouldCreateBpmnProcessById() {
    // when
    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join();

    // then instance of latest of workflow version is created
    assertThat(workflowInstance.getBpmnProcessId()).isEqualTo(processId);
    assertThat(workflowInstance.getVersion())
        .isEqualTo(secondDeployment.getWorkflows().get(0).getVersion());
    assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);

    assertWorkflowInstanceCreated(workflowInstance.getWorkflowInstanceKey());
  }

  @Test
  public void shouldCreateBpmnProcessByIdAndVersion() {
    // when
    final int version = firstDeployment.getWorkflows().get(0).getVersion();
    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .version(version)
            .send()
            .join();

    // then instance is created of first workflow version
    assertThat(workflowInstance.getBpmnProcessId()).isEqualTo(processId);
    assertThat(workflowInstance.getVersion()).isEqualTo(version);
    assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);

    assertWorkflowInstanceCreated(workflowInstance.getWorkflowInstanceKey());
  }

  @Test
  public void shouldCreateBpmnProcessByKey() {
    final Workflow firstWorkflow = firstDeployment.getWorkflows().get(0);
    final long workflowKey = firstWorkflow.getWorkflowKey();
    final int version = firstWorkflow.getVersion();

    // when
    final WorkflowInstanceEvent workflowInstance =
        clientRule.getClient().newCreateInstanceCommand().workflowKey(workflowKey).send().join();

    // then
    assertThat(workflowInstance.getBpmnProcessId()).isEqualTo(processId);
    assertThat(workflowInstance.getVersion()).isEqualTo(version);
    assertThat(workflowInstance.getWorkflowKey()).isEqualTo(workflowKey);

    assertWorkflowInstanceCreated(workflowInstance.getWorkflowInstanceKey());
  }

  @Test
  public void shouldCreateWithVariables() {
    // when
    final Map<String, Object> variables = Maps.of(entry("foo", "bar"));
    final WorkflowInstanceEvent event =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    // then
    assertWorkflowInstanceCreated(event.getWorkflowInstanceKey());
    assertThat(getInitialVariableRecords(event)).containsOnly(entry("foo", "\"bar\""));
  }

  @Test
  public void shouldCreateWithoutVariables() {
    // when
    final WorkflowInstanceEvent event =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join();

    // then
    assertWorkflowInstanceCreated(event.getWorkflowInstanceKey());
    assertThat(getInitialVariableRecords(event)).isEmpty();
  }

  @Test
  public void shouldCreateWithNullVariables() {
    // when
    final WorkflowInstanceEvent event =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables("null")
            .send()
            .join();

    // then
    assertWorkflowInstanceCreated(event.getWorkflowInstanceKey());
    assertThat(getInitialVariableRecords(event)).isEmpty();
  }

  @Test
  public void shouldThrowExceptionOnCompleteJobWithInvalidVariables() {
    // expect
    exception.expect(ClientStatusException.class);
    exception.expect(hasStatusCode(Code.INVALID_ARGUMENT));
    exception.expect(
        descriptionContains(
            "Property 'variables' is invalid: Expected document to be a root level object, but was 'ARRAY'"));

    // when
    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables("[]")
        .send()
        .join();
  }

  @Test
  public void shouldCreateWithVariablesAsMap() {
    // when
    final WorkflowInstanceEvent event =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(Collections.singletonMap("foo", "bar"))
            .send()
            .join();

    // then
    assertWorkflowInstanceCreated(event.getWorkflowInstanceKey());
    assertThat(getInitialVariableRecords(event)).containsOnly(entry("foo", "\"bar\""));
  }

  @Test
  public void shouldCreateWithVariablesAsObject() {
    final VariableDocument variables = new VariableDocument();
    variables.foo = "bar";

    // when
    final WorkflowInstanceEvent event =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    // then
    assertWorkflowInstanceCreated(event.getWorkflowInstanceKey());
    assertThat(getInitialVariableRecords(event)).containsOnly(entry("foo", "\"bar\""));
  }

  @Test
  public void shouldRejectCreateBpmnProcessByIllegalId() {
    // expected
    exception.expect(ClientStatusException.class);
    exception.expect(hasStatusCode(Code.NOT_FOUND));

    // when
    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("illegal")
        .latestVersion()
        .send()
        .join();
  }

  @Test
  public void shouldRejectCreateBpmnProcessByIllegalKey() {
    // expected
    exception.expect(ClientStatusException.class);
    exception.expect(hasStatusCode(Code.NOT_FOUND));

    // when
    clientRule.getClient().newCreateInstanceCommand().workflowKey(99L).send().join();
  }

  @Test
  public void shouldCreateWorkflowInstanceAwaitResults() {
    final Map<String, Object> variables = Maps.of(entry("foo", "bar"));
    final CreateWorkflowInstanceWithResultsResponse result = clientRule
      .getClient()
      .newCreateInstanceCommand()
      .bpmnProcessId(processId)
      .latestVersion()
      .variables(variables)
      .withResults()
      .send()
      .join();

    assertThat(result.getBpmnProcessId()).isEqualTo(processId);
    assertThat(result.getVariables()).isEqualTo(variables);
  }

  private Map<String, String> getInitialVariableRecords(WorkflowInstanceEvent event) {
    final List<Record<WorkflowInstanceCreationRecordValue>> bounds =
        RecordingExporter.workflowInstanceCreationRecords()
            .withPartitionId(Protocol.decodePartitionId(event.getWorkflowInstanceKey()))
            .limitToWorkflowInstanceCreated(event.getWorkflowInstanceKey())
            .withBpmnProcessId(processId)
            .collect(Collectors.toList());

    return WorkflowInstances.getCurrentVariables(
        event.getWorkflowInstanceKey(), bounds.get(0).getPosition(), bounds.get(1).getPosition());
  }

  public static class VariableDocument {
    public String foo;
  }
}
