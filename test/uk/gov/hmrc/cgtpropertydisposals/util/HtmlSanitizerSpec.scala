/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cgtpropertydisposals.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HtmlSanitizerSpec extends AnyWordSpec with Matchers {

  val htmlToSanitize =
    """
      |<!DOCTYPE html>
      | <html lang="en">
      |  <head>
      |    <meta charset="utf-8">
      |    <link rel="alternate" type="application/atom+xml" title="Prometheus Blog » Feed" href="/blog/feed.xml">
      |    <title>Prometheus - Monitoring system &amp; time series database</title>
      |  </head>
      |  <body>
      |
      |<div class="jumbotron">
      |  <div class="container">
      |    <h1>From metrics to insight</h1>
      |    <p class="subtitle">Power your metrics and alerting with a leading<br>open-source monitoring solution.</p>
      |    <p>
      |      <a class="btn btn-default btn-lg" href="/docs/prometheus/latest/getting_started/" role="button">Get Started</a>
      |      <a class="btn btn-default btn-lg" href="/download" role="button">Download</a>
      |    </p>
      |  </div>
      |</div>
      |
      |  <div class="container">
      |      <div class="row logos">
      |          <a href="https://amadeus.com/"><img src="assets/company-logos/Amadeus.png"/></a>
      |      </div>
      |    </div>
      |
      |  <hr>
      |
      |<footer>
      |  <p class="pull-left">
      |    &copy; Prometheus Authors 2014-2020 | Documentation Distributed under CC-BY-4.0
      |  </p>
      |  <p class="pull-left">
      |     &copy; 2020 The Linux Foundation. All rights reserved. The Linux Foundation has registered trademarks and uses trademarks. For a list of trademarks of The Linux Foundation, please see our <a href="https://www.linuxfoundation.org/trademark-usage">Trademark Usage</a> page.
      |  </p>
      |</footer>
      |
      |</div>
      |
      |    <!-- Bootstrap core JavaScript
      |    ================================================== -->
      |    <!-- Placed at the end of the document so the pages load faster -->
      |    <script src="https://code.jquery.com/jquery-2.2.2.min.js" integrity="sha256-36cp2Co+/62rEAAYHLmRCPIych47CvdM+uTBJwSzWjI=" crossorigin="anonymous"></script>
      |    <script src="/assets/bootstrap-3.3.1/js/bootstrap.min.js"></script>
      |    <script src="/assets/docs.js"></script>
      |    <!-- IE10 viewport hack for Surface/desktop Windows 8 bug -->
      |    <script src="/assets/ie10-viewport-bug-workaround.js"></script>
      |    <!-- Google Analytics -->
      |    <script>
      |      (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
      |      (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
      |      m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
      |      })(window,document,'script','//www.google-analytics.com/analytics.js','ga');
      |
      |      ga('create', 'UA-58468480-1', 'auto');
      |      ga('send', 'pageview');
      |    </script>
      |  </body>
      |</html>
      |
      |
      |""".stripMargin

  val sanitizedHtml =
    """
      |<html>
      |   <div>
      |      <div>
      |         <h1>From metrics to insight</h1>
      |         <p>Power your metrics and alerting with a leading<br />open-source monitoring solution.</p>
      |         <p>
      |            Get Started
      |            Download
      |         </p>
      |      </div>
      |   </div>
      |   <div>
      |      <div>
      |      </div>
      |   </div>
      |   <hr />
      |   <p>
      |      © Prometheus Authors 2014-2020 | Documentation Distributed under CC-BY-4.0
      |   </p>
      |   <p>
      |      © 2020 The Linux Foundation. All rights reserved. The Linux Foundation has registered trademarks and uses trademarks. For a list of trademarks of The Linux Foundation, please see our Trademark Usage page.
      |   </p>
      |</html>
      |""".stripMargin

  assert(
    HtmlSanitizer.sanitize(htmlToSanitize).map(s => s.replaceAll("\\s", "")) === Some(
      sanitizedHtml.replaceAll("\\s", "")
    )
  )

}
