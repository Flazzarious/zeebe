/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.response;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceResultRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceResultIntent;
import org.agrona.DirectBuffer;

public class SendResponseHandler implements TypedRecordProcessor<WorkflowInstanceResultRecord> {

  ElementInstanceState state;
  WorkflowInstanceResultRecord completedRecord = new WorkflowInstanceResultRecord();

  public SendResponseHandler(ElementInstanceState state) {
    this.state = state;
  }

  @Override
  public void processRecord(
      TypedRecord<WorkflowInstanceResultRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {
    final WorkflowInstanceResultRecord workflowRecord = record.getValue();
    final DirectBuffer variablesAsDocument =
        state.getVariablesState().getVariablesAsDocument(workflowRecord.getWorkflowInstanceKey());

    completedRecord.reset();
    completedRecord
        .setWorkflowInstanceKey(workflowRecord.getWorkflowInstanceKey())
        .setWorkflowKey(workflowRecord.getWorkflowKey())
        .setVariables(variablesAsDocument)
        .setBpmnProcessId(workflowRecord.getBpmnProcessId())
        .setVersion(workflowRecord.getVersion());

    streamWriter.appendFollowUpEvent(
        record.getKey(), WorkflowInstanceResultIntent.COMPLETED, completedRecord);
    responseWriter.writeEventOnCommand(
        record.getKey(), WorkflowInstanceResultIntent.COMPLETED, completedRecord, record);
  }
}
