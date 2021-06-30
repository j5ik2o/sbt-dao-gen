package com.github.j5ik2o.sbt.dao.gen

import org.seasar.util.lang.StringUtil
import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin

object SbtDaoGenPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  override def requires: JvmPlugin.type = JvmPlugin

  object autoImport extends SbtDaoGenKeys
  import SbtDaoGenKeys._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    generator / enableManagedClassPath := true,
    generator / driverClassName := "",
    generator / jdbcUrl := "",
    generator / jdbcUser := "",
    generator / jdbcPassword := "",
    generator / schemaName := None,
    generator / templateDirectory := baseDirectory.value / "templates",
    generator / templateNameMapper := { _: String =>
      "template.ftl"
    },
    generator / propertyTypeNameMapper := identity,
    generator / tableNameFilter := { _: String =>
      true
    },
    generator / propertyNameMapper := { columnName: String =>
      StringUtil.decapitalize(StringUtil.camelize(columnName))
    },
    generator / classNameMapper := { tableName: String =>
      Seq(StringUtil.camelize(tableName))
    },
    generator / outputDirectoryMapper := { _: String =>
      (Compile / sourceManaged).value
    }
    /*
    generateAll in generator := SbtDaoGenerator.generateAllTask.value,
    generateMany in generator := SbtDaoGenerator.generateManyTask.evaluated,
    generateOne in generator := SbtDaoGenerator.generateOneTask.evaluated
     */
  )
}
