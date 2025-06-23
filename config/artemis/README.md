# How to use

1. Start Up artemis `podman compose -f config/artemis/docker-compose.yml up -d`
2. Start Up the swatch contracts service using `UMB_ENABLED=true` because it's disabled on DEV mode.
3. Send messages: `podman exec artemis utils/send_amqp_message.py --address VirtualTopic.services.productservice.Product --content '<the content>'`
Example:
```
podman exec artemis utils/send_amqp_message.py --address VirtualTopic.services.productservice.Product --content '{ "occurredOn" : "2025-03-29", "productCode" : "RH0180191", "productCategory": "Parent SKU", "eventType" : "Create" }'
```