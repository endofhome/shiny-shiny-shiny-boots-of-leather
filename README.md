[![Build Status](https://travis-ci.org/forty9er/shiny-shiny-shiny-boots-of-leather.svg?branch=master)](https://travis-ci.org/forty9er/https://travis-ci.org/forty9er/shiny-shiny-shiny-boots-of-leather)

## Shiny shiny, shiny boots of leather.
##### Well, you wouldn't want your personal assistant wearing anything else, would you?
![boots](https://www.dropbox.com/s/jr4pr610xguxovc/boots.jpg?raw=1)

### What is this?
A pluggable job scheduling system written in Kotlin.

### What is a job?
At the time of writing there is only one job, which performs a search query against a Gmail account and forwards the latest email matching that query to a specified email address. The current state of the application is simple, so is stored in a blob of JSON in a file in Dropbox.

A job is defined as a Kotlin class containing some code - the job itself, and a companion object containing required configuration values.

The entire app is run on Heroku using the Heroku Scheduler plugin, but it could just as easily be run via a CRON job or similar. 

### What do I need to run it?
* kotlin
* Any necessary third-party accounts/API access configured (depending on the jobs being run)
* The configuration values required for each job. These can be provided in files in the `credentials` directory or as environment variables. There is a `JOB_SPEC.md` file in the package for each job. See the example of the GmailForwarder job [here](src/main/kotlin/jobs/GmailForwarderJob/JOB_SPEC.md).

### How do I run it?
* Clone the repo
* Build the code and run the tests using Gradle `./gradlew clean check`
* Run it `./gradlew run` (nb. this will blow up if required configuration values are missing)

### Technical notes
Each push to GitHub builds on Travis CI and each successful build is deployed to Heroku.
