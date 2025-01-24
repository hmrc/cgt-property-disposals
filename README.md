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

### License

This code is open source software licensed under
the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
