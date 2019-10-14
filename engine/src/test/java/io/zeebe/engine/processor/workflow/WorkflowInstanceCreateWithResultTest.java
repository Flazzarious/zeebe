/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;

import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceResultRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceResultIntent;
import io.zeebe.test.util.MsgPackUtil;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.mockito.Mockito;

public class WorkflowInstanceCreateWithResultTest {
  private static final String PROCESS_ID = "process";
  private static final BpmnModelInstance SERVICE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID).startEvent("start").endEvent("end").done();

  @Rule public Timeout timeoutRule = new Timeout(2, TimeUnit.MINUTES);
  private final StreamProcessorRule envRule = new StreamProcessorRule();
  private final WorkflowInstanceStreamProcessorRule streamProcessorRule =
      new WorkflowInstanceStreamProcessorRule(envRule);
  @Rule public RuleChain chain = RuleChain.outerRule(envRule).around(streamProcessorRule);

  @Test
  public void shouldSendResultAfterCompletion() {
    // given
    streamProcessorRule.deploy(SERVICE_TASK_WORKFLOW);

    streamProcessorRule.createWorkflowInstanceBlocking(r -> r.setBpmnProcessId(PROCESS_ID));

    // then
    waitUntil(() -> envRule.events().withIntent(WorkflowInstanceResultIntent.SEND).exists());

    Mockito.verify(envRule.getCommandResponseWriter(), times(1))
        .intent(WorkflowInstanceResultIntent.COMPLETED);
  }

  @Test
  public void shouldSendResultWithVariablesAfterCompletion() {
    // given
    streamProcessorRule.deploy(SERVICE_TASK_WORKFLOW);

    final String variables = "{'x':1}";
    streamProcessorRule.createWorkflowInstanceBlocking(
        r -> r.setBpmnProcessId(PROCESS_ID).setVariables(MsgPackUtil.asMsgPack(variables)));

    // then
    final WorkflowInstanceResultRecord result =
        envRule
            .events()
            .onlyWorkflowInstanceResultRecords()
            .withIntent(WorkflowInstanceResultIntent.SEND)
            .findFirst()
            .get()
            .getValue();

    assertThat(result.getVariables().containsKey("x")).isTrue();
    assertThat(result.getVariables().get("x")).isEqualTo(1);

    Mockito.verify(envRule.getCommandResponseWriter(), times(1))
        .intent(WorkflowInstanceCreationIntent.COMPLETED_WITH_RESULT);
  }
}
