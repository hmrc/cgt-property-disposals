# cgt-property-disposals

For more context of Capital Gain Tax on UK Property (CGT),
see [here](https://github.com/hmrc/cgt-property-disposals-frontend#cgt-property-disposals-frontend).

This service performs the following main functions:

- allows for the management of CGT accounts in the Enterprise Tax Management Platform (ETMP) via the Data
  Exchange Service (DES)
- submits documents to the Digital Mail Service (DMS) when a return is submitted
- calculates how much CGT tax is due when it is possible
- store and returns draft returns
- stores CGT figures (e.g. CGT rates) which change each tax year
- manages callbacks from the Upscan service when dealing with uploading files

## How to run

Use `sbt run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes` in a terminal to run this service. This service runs on port `7021` by default.

The other services this service relies on can be run using the `CGTPD_ALL` service manager profile and stopping the
`CGTPD` service if you want to run this service from source, e.g.:

```
sm2 --start CGTPD_ALL
sm2 --stop CGTPD
```   
## Run Tests
- Run Unit Tests:  `sbt test`
- Run Integration Tests: `sbt it:test`
- Run Unit and Integration Tests: `sbt test it:test`
- Run Unit and Integration Tests with coverage report: `sbt clean compile coverage test it:test coverageReport`

## How to test Dms submission

In order to verify successful submission of envelopes to Dms, run the following services:

```
sm2 --start INTERNAL_AUTH_FRONTEND
sm2 --start DMS_SUBMISSION_ADMIN_FRONTEND
```

Then, navigate to http://localhost:8471/test-only/sign-in and complete the login form:

| Field              | Value                                                                                  |
|--------------------|----------------------------------------------------------------------------------------|
| Principal          | Any string                                                                             |
| Redirect Url       | http://localhost:8224/dms-submission-admin-frontend/cgt-property-disposals/submissions |
| Resource Type      | dms-submission                                                                         |
| Resource Locations | cgt-property-disposals                                                                 |
| Action             | READ                                                                                   |

You should now see a list of submissions, click on each link to view the submission metadata.

In order to view the contents of the envelope, the form.pdf and any attachments, run the following service:

```
sm2 --start INTERNAL_AUTH_FRONTEND
sm2 --start OBJECT_STORE_ADMIN_FRONTEND
```

Then, navigate to http://localhost:8471/test-only/sign-in and complete the login form:

| Field              | Value                                                                    |
|--------------------|--------------------------------------------------------------------------|
| Principal          | Any string                                                               |
| Redirect Url       | http://localhost:8467/object-store-admin-frontend/objects/dms-submission |
| Resource Type      | object-store-admin-frontend                                              |
| Resource Locations | *                                                                        |
| Action             | READ                                                                     |

You should now see a folder titled `sdes/cgt-property-disposals`, click on it. You will now see a list of zip files,
each of which can be downloaded.

### License

This code is open source software licensed under
the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
