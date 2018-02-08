/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.transport.impl;

import java.time.Duration;

import io.zeebe.dispatcher.*;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ServerOutput;

public class TransportContext
{
    private int messageMaxLength;
    private Duration channelKeepAlivePeriod;

    private ServerOutput serverOutput;
    private ClientOutput clientOutput;

    private Dispatcher receiveBuffer;
    private Dispatcher sendBuffer;

    private RemoteAddressListImpl remoteAddressList;

    private ClientRequestPool clientRequestPool;

    private FragmentHandler receiveHandler;
    private SendFailureHandler sendFailureHandler;

    private ServerSocketBinding serverSocketBinding;

    private TransportChannelFactory channelFactory = new DefaultChannelFactory();

    public int getMessageMaxLength()
    {
        return messageMaxLength;
    }

    public void setMessageMaxLength(int messageMaxLength)
    {
        this.messageMaxLength = messageMaxLength;
    }

    public ServerOutput getServerOutput()
    {
        return serverOutput;
    }

    public void setServerOutput(ServerOutput serverOutput)
    {
        this.serverOutput = serverOutput;
    }

    public ClientOutput getClientOutput()
    {
        return clientOutput;
    }

    public void setClientOutput(ClientOutput clientOutput)
    {
        this.clientOutput = clientOutput;
    }

    public Dispatcher getReceiveBuffer()
    {
        return receiveBuffer;
    }

    public void setReceiveBuffer(Dispatcher receiveBuffer)
    {
        this.receiveBuffer = receiveBuffer;
    }

    public RemoteAddressListImpl getRemoteAddressList()
    {
        return remoteAddressList;
    }

    public void setRemoteAddressList(RemoteAddressListImpl remoteAddressList)
    {
        this.remoteAddressList = remoteAddressList;
    }

    public ClientRequestPool getClientRequestPool()
    {
        return clientRequestPool;
    }

    public void setClientRequestPool(ClientRequestPool clientRequestPool)
    {
        this.clientRequestPool = clientRequestPool;
    }

    public void setReceiveHandler(FragmentHandler receiveHandler)
    {
        this.receiveHandler = receiveHandler;
    }

    public FragmentHandler getReceiveHandler()
    {
        return receiveHandler;
    }

    public SendFailureHandler getSendFailureHandler()
    {
        return sendFailureHandler;
    }

    public void setSendFailureHandler(SendFailureHandler sendFailureHandler)
    {
        this.sendFailureHandler = sendFailureHandler;
    }

    public ServerSocketBinding getServerSocketBinding()
    {
        return serverSocketBinding;
    }

    public void setServerSocketBinding(ServerSocketBinding serverSocketBinding)
    {
        this.serverSocketBinding = serverSocketBinding;
    }

    public void setChannelKeepAlivePeriod(Duration channelKeepAlivePeriod)
    {
        this.channelKeepAlivePeriod = channelKeepAlivePeriod;
    }

    public Duration getChannelKeepAlivePeriod()
    {
        return channelKeepAlivePeriod;
    }

    public void setChannelFactory(TransportChannelFactory channelFactory)
    {
        this.channelFactory = channelFactory;
    }

    public TransportChannelFactory getChannelFactory()
    {
        return channelFactory;
    }

    public void setSendBuffer(Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
    }

    public Dispatcher getSetSendBuffer()
    {
        return sendBuffer;
    }
}
