package uk.gov.hmrc.pillar2externalteststub.utils

import scala.io.Source

object ResourceHelper {

  def resourceAsString(resourcePath: String): Option[String] =
    Option(getClass.getResourceAsStream(resourcePath)) map { is =>
      Source.fromInputStream(is).getLines.mkString("\n")
    }

}

