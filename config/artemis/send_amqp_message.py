#!/usr/bin/env python3

import argparse
import json
from proton import Message
from proton.handlers import MessagingHandler
from proton.reactor import Container


class SendAMQPMessage(MessagingHandler):
    def __init__(self, broker, address, content, debug):
        super().__init__()
        self.server = f"amqp://{broker}"
        self.address = address
        self.debug = debug

        try:
            body = json.loads(content)
        except json.JSONDecodeError as e:
            raise ValueError(f"Invalid JSON for message content: {e}")

        self.message = Message(
            body=body,
            content_type="application/json"
        )

    def on_start(self, event):
        if self.debug:
            print(f"🚀 Connecting to {self.server} with SASL PLAIN (guest/guest)")
        conn = event.container.connect(
            self.server,
            user="guest",
            password="guest",
            sasl_enabled=False,
            allowed_mechs="PLAIN",
            allow_insecure_mechs=True
        )
        event.container.create_sender(conn, self.address)

    def on_sendable(self, event):
        if self.debug:
            print(f"📤 Sending message to '{self.address}': {self.message.body}")
        event.sender.send(self.message)
        event.sender.close()
        event.connection.close()

    def on_transport_error(self, event):
        print(f"❌ Transport error: {event.transport.condition}")

    def on_disconnected(self, event):
        if self.debug:
            print("❌ Disconnected from broker.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Send a message over AMQP.")
    parser.add_argument("--broker", required=True, help="AMQP broker address (e.g. localhost:5672)")
    parser.add_argument("--address", required=True, help="Queue or topic name to send to")
    parser.add_argument("--content", required=True, help="JSON string as message body")
    parser.add_argument("--debug", action="store_true", help="Enable debug output")

    args = parser.parse_args()

    try:
        Container(SendAMQPMessage(args.broker, args.address, args.content, args.debug)).run()
    except ValueError as e:
        print(f"Error: {e}")
