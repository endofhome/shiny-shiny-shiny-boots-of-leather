## NewsletterGmailer job

### What does it do?
This job delivers email reminders to the membership of a group on a cleaning rota. It can be configured to run on any day of the week, at any time in any time zone.
The behaviour flip-flops each time it is successfully run - on the first pass it will email all of the members, and on the second pass it will inform only a selected member, which is advanced through the membership list on each pass. The specific messages are configured by the user.
The current state of the application and current list of members are stored in blobs of JSON, each in a file in Dropbox.
The email subject/body and error messages use [Handlebars](https://github.com/jknack/handlebars.java) for templating.

### Required configuration:

| Config item|Description
|------------- |------------- |
|```NEWSLETTER_GMAILER_JOB_NAME``` |  A name for the job, ie. "John's job"
|```NEWSLETTER_GMAILER_GMAIL_CLIENT_SECRET ```| 
|```NEWSLETTER_GMAILER_GMAIL_ACCESS_TOKEN```|
|```NEWSLETTER_GMAILER_GMAIL_REFRESH_TOKEN```|
|```NEWSLETTER_GMAILER_DROPBOX_ACCESS_TOKEN```|
|```NEWSLETTER_GMAILER_RUN_ON_DAYS``` | A list of days of the week you want the job to run on, ie. "Monday, Wednesday, Thursday", or just "Monday"
|```NEWSLETTER_GMAILER_RUN_AFTER_TIME```| A time at which it is acceptable to run the job. ie. "08:15"
|```NEWSLETTER_GMAILER_RUN_AFTER_TZDB``` | The time zone in which RUN_AFTER_TIME is applicable. ie. "Europe/London"
|```NEWSLETTER_GMAILER_FROM_ADDRESS``` | The sender's email address
|```NEWSLETTER_GMAILER_FROM_FULLNAME``` | The name of the sender that you want to be displayed in your outgoing email
|```NEWSLETTER_GMAILER_BCC_ADDRESS``` | A recipient's email address, they will receive emails in BCC
|```NEWSLETTER_GMAILER_SUBJECT_A``` | The subject for the 'first pass' of emails. A Handlebars template.
|```NEWSLETTER_GMAILER_SUBJECT_B``` | The subject for the 'second' pass of emails. A Handlebars template.
|```NEWSLETTER_GMAILER_BODY_A``` | The body for the 'first' pass of emails. A Handlebars template.
|```NEWSLETTER_GMAILER_BODY_B``` | The body for the 'second' pass of emails. A Handlebars template.
|```NEWSLETTER_GMAILER_FOOTER``` | A footer which will be used in all the emails.
