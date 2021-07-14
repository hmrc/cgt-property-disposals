/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.dms

import akka.util.ByteString

final case class FileAttachment(
  key: String,
  filename: String,
  contentType: Option[String],
  data: Seq[ByteString]
)

object FileAttachment {

  implicit class FileAttachmentOps(private val f: FileAttachment) extends AnyVal {
    // It replaces all invalid characters available in Filename with underscore(_)
    // to avoid issues with Windows OS
    def replaceAllInvalidCharsWithUnderScore(): FileAttachment = {
      // \x00-\x1F ==> [1-32]
      // invalidASCIIChars ++:  invalidASCIIChars2
      val invalidASCIIChars   = (0 to 31).map(_.toString).toList ++: "00,01,02,03,04,05,06,07,08,09".split(",").toList
      val invalidSpecialChars = "[<>:/\"|?*\\\\]".r

      val filenameWithExtension = f.filename.split("\\.(?=[^\\.]+$)")

      val updatedFilename =
        if (invalidASCIIChars.contains(filenameWithExtension(0))) "_"
        else invalidSpecialChars.replaceAllIn(filenameWithExtension(0), "_")

      val fullUpdatedFilename =
        if (filenameWithExtension.length > 1) s"$updatedFilename.${filenameWithExtension(1)}"
        else updatedFilename

      f.copy(filename = fullUpdatedFilename)
    }
  }

}
