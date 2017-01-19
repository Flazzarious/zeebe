package org.camunda.tngp.broker.util.msgpack.value;

import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;

public class LongValue extends BaseValue
{
    private long value;

    public long getValue()
    {
        return value;
    }

    public void setValue(long val)
    {
        this.value = val;
    }

    @Override
    public void reset()
    {
        value = 0;
    }

    @Override
    public void writeJSON(StringBuilder builder)
    {
        builder.append(value);
    }

    @Override
    public void write(MsgPackWriter writer)
    {
        writer.writeInteger(value);
    }

    @Override
    public void read(MsgPackReader reader)
    {
        value = reader.readInteger();
    }

    @Override
    public int getEncodedLength()
    {
        return MsgPackWriter.getEncodedLongValueLength(value);
    }

}
