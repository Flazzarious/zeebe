/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.subprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.EmbeddedSubProcessBuilder;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class EventSubprocessTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldTriggerEventSubprocess() {
    // given
    final BpmnModelInstance model = createEventSubProc();

    // when
    final long workflowKey =
        ENGINE
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0)
            .getWorkflowKey();
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("proc").withVariable("innerKey", "123").create();

    // then
    final Record<WorkflowInstanceRecordValue> timerTriggered =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_OCCURRED).getFirst();
    Assertions.assertThat(timerTriggered.getValue())
        .hasWorkflowKey(workflowKey)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasBpmnElementType(BpmnElementType.START_EVENT)
        .hasElementId("event_sub_start");

    assertEventSubprocessLifecycle(timerTriggered);
  }

  @Test
  public void shouldTriggerNestedEventSubproc() {
    // given
    final BpmnModelInstance model = createNestedEventSubproc();
    ENGINE.deployment().withXmlResource(model).deploy();

    // when
    ENGINE.workflowInstance().ofBpmnProcessId("proc").create();

    // then
    final Record<WorkflowInstanceRecordValue> timerTriggered =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_OCCURRED).getFirst();

    assertEventSubprocessLifecycle(timerTriggered);
  }

  @Test
  public void shouldPropagateVariablesToEventSubprocess() {
    // given
    final BpmnModelInstance model = createEventSubprocWithTask();
    ENGINE
        .deployment()
        .withXmlResource(model)
        .deploy()
        .getValue()
        .getDeployedWorkflows()
        .get(0)
        .getWorkflowKey();

    // when
    final long wfInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("proc")
            .withVariable("randomVariable", 159)
            .create();

    final long jobCompletePosition =
        ENGINE
            .job()
            .ofInstance(wfInstanceKey)
            .withType("sub_task")
            .withVariables(Map.of("randomVariable", 159))
            .complete()
            .getPosition();

    // then
    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords()
            .skipUntil(r -> r.getPosition() > jobCompletePosition)
            .limit(9)
            .asList();

    assertThat(events)
        .extracting(Record::getIntent, e -> e.getValue().getElementId())
        .containsExactly(
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_task"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_task"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "after_task_flow"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_proc"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_proc"));
  }

  private void assertEventSubprocessLifecycle(Record<WorkflowInstanceRecordValue> timerTriggered) {
    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords()
            .skipUntil(r -> r.getPosition() > timerTriggered.getPosition())
            .limit(13)
            .asList();

    assertThat(events)
        .extracting(Record::getIntent, e -> e.getValue().getElementId())
        .containsExactly(
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "event_sub_proc"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "event_sub_proc"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_start"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "event_sub_flow"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_proc"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_proc"));
  }

  private static BpmnModelInstance createEventSubProc() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("proc");
    builder
        .eventSubProcess("firstSubProcess")
        .startEvent("start_sub_1")
        .message(b -> b.name("msg").zeebeCorrelationKey("innerKey"))
        .endEvent();
    builder
        .eventSubProcess("event_sub_proc")
        .startEvent("event_sub_start")
        .timerWithDuration("PT1S")
        .sequenceFlowId("event_sub_flow")
        .endEvent("event_sub_end");

    return builder
        .startEvent("start_proc")
        .serviceTask("task", t -> t.zeebeTaskType("type"))
        .endEvent("sub_proc")
        .done();
  }

  private static BpmnModelInstance createNestedEventSubproc() {
    final EmbeddedSubProcessBuilder embeddedBuilder =
        Bpmn.createExecutableProcess("proc")
            .startEvent("proc_start")
            .subProcess("sub_proc")
            .embeddedSubProcess();
    embeddedBuilder
        .eventSubProcess("event_sub_proc")
        .startEvent("event_sub_start")
        .timerWithDuration("PT1S")
        .sequenceFlowId("event_sub_flow")
        .endEvent("event_sub_end");
    return embeddedBuilder
        .startEvent("sub_start")
        .serviceTask("task", t -> t.zeebeTaskType("type"))
        .endEvent("sub_end")
        .done();
  }

  private static BpmnModelInstance createEventSubprocWithTask() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("proc");

    builder
        .eventSubProcess("event_sub_proc")
        .startEvent("event_sub_start")
        .timerWithDuration("PT1S")
        .serviceTask("event_sub_task", b -> b.zeebeTaskType("sub_task"))
        .sequenceFlowId("after_task_flow")
        .endEvent("event_sub_end");

    return builder
        .startEvent("start_proc")
        .serviceTask("task", t -> t.zeebeTaskType("type"))
        .endEvent("sub_proc")
        .done();
  }
}
