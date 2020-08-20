:orphan:

# User Support Service

The support-service is implementing a basic system for support messages. 

At the moment this service is sending messages to the #devsupport/#support
channels of slack. The messages are sent to slack via their webhooks feature.
This service is not specified only to work with slack, but can be hooked up to 
any chat/mail service that supports webhooks.

.. figure:: /backend/support-service/wiki/SupportFlow.png
   :width: 100%
   :align: center

## Support ticket format

- User information
  - UCloud username
  - Security role
  - Real name
- Technical info
  - Request ID 
  - User agent (Browser)
- Type (Bug, Suggestion)
- Custom message from the user
