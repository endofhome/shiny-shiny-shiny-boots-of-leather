#### GmailForwarder job

##### What does it do?
This job performs a search query against a Gmail account and forwards the latest email matching that query to a specified email address. The current state of the application is simple, so is stored in a blob of JSON in a file in Dropbox.

#### Required configuration:

| Config value|Description
|------------- |------------- |
|```GMAIL_FORWARDER_JOB_NAME```| A name for the job, ie. "John's job"
|```GMAIL_FORWARDER_GMAIL_CLIENT_SECRET```| 
|```GMAIL_FORWARDER_GMAIL_ACCESS_TOKEN```|
|```GMAIL_FORWARDER_GMAIL_REFRESH_TOKEN```|
|```GMAIL_FORWARDER_DROPBOX_ACCESS_TOKEN```|
|```GMAIL_FORWARDER_GMAIL_QUERY```| The query you want to run against your gmail account. The API same syntax as a search using the Gmail GUI
|```GMAIL_FORWARDER_RUN_ON_DAYS```| A list of days of each month you want the job to run on, ie. "1,5,15,20", or just "1"
|```GMAIL_FORWARDER_FROM_ADDRESS```| The sender's email address
|```GMAIL_FORWARDER_FROM_FULLNAME```| The name of the sender that you want to be displayed in your outgoing email
|```GMAIL_FORWARDER_TO_ADDRESS```| The recipient's email address
|```GMAIL_FORWARDER_TO_FULLNAME```| The name of the recipient that you want to be displayed in your outgoing email
|```GMAIL_FORWARDER_BCC_ADDRESS```| An email address that you would like to include in BCC