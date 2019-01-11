[![Build Status](https://travis-ci.org/endofhome/shiny-shiny-shiny-boots-of-leather.svg?branch=master)](https://travis-ci.org/endofhome/shiny-shiny-shiny-boots-of-leather)

## Shiny shiny, shiny boots of leather.
##### Well, you wouldn't want your personal assistant wearing anything else, would you?
![boots](https://www.dropbox.com/s/jr4pr610xguxovc/boots.jpg?raw=1)

### What is this?
A pluggable job scheduling system written in Kotlin.

### What is a job?
At the time of writing there are two jobs included in this repo:
  1. Gmail Forwarder job, which performs a search query against a Gmail account and forwards the latest email matching that query to a specified email address. The current state of the application is simple, so is stored in a blob of JSON in a file in Dropbox.
  2. Cleaning Rota Gmailer job, which alternates between sending one of two possible messages to either one or a group of recipients as an email via the Gmail API. The current state of the application is simple, so is stored in a blob of JSON in a file in Dropbox.

A job is defined as a Kotlin class containing some code - the job itself, and a companion object containing required configuration values.

The entire app is run on Heroku using the Heroku Scheduler plugin, but it could just as easily be run via a CRON job or similar. 

### What do I need to run it?
* Kotlin/JVM
* Any necessary third-party accounts/API access configured (depending on the jobs being run)
* The configuration values required for each job. These can be provided in files in the `credentials` directory or as environment variables. There is a `JOB_SPEC.md` file in the package for each job. See the example of the GmailForwarder job [here](src/main/kotlin/jobs/GmailForwarderJob/JOB_SPEC.md).

### How do I run it?
* Clone the repo
* Build the code and run the tests using Gradle `./gradlew clean check`
* Run it `./gradlew run` (nb. this will blow up if required configuration values are missing)

### Technical notes
#### Configuration
The application is able to validate that necessary environment variables have been provided for each job. Each job has a `RequiredConfig`, which specifies the environment variables required to run. The `config` package contains a `Configurator`, which performs a recursive search of system environment variables and an optional user-provided config file. A `Configuration` is returned, which self-validates against the `RequiredConfig` during object construction, and blows up with a useful error message if any variables are missing. Assuming that the Configuration was successfully constructed, it provides the user with safe public methods on which to retrieve the required values.
This is my first attempt at solving this kind of problem, and whilst I'm reasonably happy with it I wonder if it is a little convoluted. The sealed class that contains each job's required configuration is a little curious, I would have preferred an enum for this case but this is the only way I could find to make it work due to the constraints of the language. I considered whether this package should actually be a separate library but it is quite specific to the application due to the prefix which is specified per-job. It also doesn't provide support for type-safe and custom mapped deserialisation of environment variables as libraries like [Konfig](https://github.com/npryce/konfig) and [Configur8](https://github.com/daviddenton/configur8) do. Nevertheless, it *works* and it's *useful*.

#### Result
This is my first attempt (outside of work) to incorporate a `Result` monad into one of my projects. If you're unfamiliar with the concept it's essentially a data type that can hold a type that represents either a successful or failure value and explicitly does not hold the opposite value. It is a type that is available in the Rust standard library and is very similar to an `Either` as seen in Haskell, Scala and other languages (but unavailable in the Kotlin standard library). I'm quite happy with how this has worked out (though it was a little painful to introduce part way into writing the first job). Just don't ask me why the `flatmap` function on a `Result` is so named!
For the uninitiated, here's [an interesting article about `Result` vs. "traditional" error handling in Java](http://oneeyedmen.com/failure-is-not-an-option-part-1.html).

#### CI / Deployment
Each push to GitHub builds on Travis CI and a successful build is deployed to Heroku.
