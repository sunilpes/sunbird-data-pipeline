package org.ekstep.ep.samza.task;

import org.apache.samza.system.IncomingMessageEnvelope;
import org.ekstep.ep.samza.domain.Event;

import java.util.Map;

public class EsIndexerSecondarySource {

    private IncomingMessageEnvelope envelope;

    public EsIndexerSecondarySource(IncomingMessageEnvelope envelope) {
        this.envelope = envelope;
    }

    public Event getEvent() {
        return new Event((Map<String, Object>) envelope.getMessage());
    }
}