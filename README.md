# Critical Updates

This program gets webhooks from Zendesk when tickets are updated.

Much of the business logic is specific to how we use Zendesk. 

Upon receiving a webhook, the program checks whether the ticket's critical update field is set.
If so, 
* it updates the list of followers in Zendesk with the IDs of the AE and SA.
* it updates that ticket's tags to include a tag to indicate that ticket has been processed.
If the critical update field is not sent, but the processed tag exists, that tag is deleted and the ticket is updated.

## How to install the program on your local Mac
* Checkout the code `git clone https://github.com/rcprcp/CriticalUpdates.git`
* `cd Criticalupdates`
* `mvn clean package`  This should create a standalone jar file with all dependencies.
* You will need to set up the following shell variables (or configure IntelliJ RUn profile) for credentials.
  * ZENDESK_USER
  * ZENDESK_TOKEN
  * ZENDESK_URL
* `java -jar target/CriticalUpdates-1.0-SNAPSHOT-jar-with-dependencies.jar`

## How to test:
These instructions are for setting up a local (on your Mac) testing environment.
In this way you can use the Intellij debugger, or even use something like YourKit profiler
in order to check the application's performance. 

* Get ngrok `https://ngrok.com/` (only need to do this once) :)
* Start ngrok (`ngrok http 5000`) This will return an "ngrok" url to which we should send the Zendesk Webhooks.
* Start the app standalone: `java -jar target/CriticalUpdates-1.0-SNAPSHOT-jar-with-dependencies.jar` or in the Intellij Debugger 
* Update (or create) the Zendesk webhook that will send the webhook request to our application - currently the only field that is used from the WehBook is the ticketId.
* The program should start to get updates via Webhooks when Zendesk Tickets are updated.
* You can check on the status of the process via the healthcheck report `https://localhost:5002/health`