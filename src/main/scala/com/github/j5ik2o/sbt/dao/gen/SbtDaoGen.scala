package com.github.j5ik2o.sbt.dao.gen

import com.github.j5ik2o.sbt.dao.gen.SbtDaoGenKeys._
import com.github.j5ik2o.sbt.dao.gen.utils.Loan

import java.io._
import java.sql.{ Connection, Driver }
import org.seasar.util.lang.StringUtil
import sbt.Keys._
import sbt.complete.Parser
import sbt.internal.inc.classpath.{ ClasspathUtil, ClasspathUtilities }
import sbt.{ File, _ }
import schemacrawler.schema.{ Schema, Table }
import schemacrawler.schemacrawler.SchemaCrawlerOptionsBuilder
import schemacrawler.tools.utility.SchemaCrawlerUtility

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.{ Success, Try }

trait SbtDaoGen {
  import complete.DefaultParsers._

  private val oneStringParser: Parser[String] = token(Space ~> StringBasic, "table name")

  private val manyStringParser: Parser[Seq[String]] = token(Space ~> StringBasic, "table name") +

  case class GeneratorContext(
      logger: Logger,
      connection: Connection,
      classNameMapper: String => Seq[String],
      typeNameMapper: String => String,
      tableNameFilter: String => Boolean,
      propertyNameMapper: String => String,
      schemaName: Option[String],
      templateDirectory: File,
      templateNameMapper: String => String,
      outputDirectoryMapper: String => File
  )

  /** JDBCコネクションを取得する。
    *
    * @param classLoader     クラスローダ
    * @param driverClassName ドライバークラス名
    * @param jdbcUrl         JDBC URL
    * @param jdbcUser        JDBCユーザ
    * @param jdbcPassword    JDBCユーザのパスワード
    * @return JDBCコネクション
    */
  private[generator] def getJdbcConnection(
      classLoader: ClassLoader,
      driverClassName: String,
      jdbcUrl: String,
      jdbcUser: String,
      jdbcPassword: String
  )(implicit logger: Logger): Try[Connection] = Try {
    logger.debug(s"getJdbcConnection($classLoader, $driverClassName, $jdbcUrl, $jdbcUser, $jdbcPassword): start")
    var connection: Connection = null
    try {
      val driver = classLoader.loadClass(driverClassName).newInstance().asInstanceOf[Driver]
      val info   = new java.util.Properties()
      info.put("user", jdbcUser)
      info.put("password", jdbcPassword)
      connection = driver.connect(jdbcUrl, info)
    } finally {
      logger.debug(s"getJdbcConnection: finished = $connection")
    }
    connection
  }

  def generateAllTask: Def.Initialize[Task[Seq[File]]] = Def.taskDyn {
    implicit val logger = streams.value.log
    logger.info("sbt-dao-generator: generateAll task")
    logger.info("driverClassName = " + (generator / driverClassName).value.toString)
    logger.info("jdbcUrl = " + (generator / jdbcUrl).value.toString)
    logger.info("jdbcUser = " + (generator / jdbcUser).value.toString)
    logger.info("schemaName = " + (generator / schemaName).value.getOrElse(""))
    val enableManagedClassPathValue = (generator / enableManagedClassPath).value
    val managedClasspathData        = (Compile / managedClasspath).value.map(_.data)
    val driverClassNameValue        = (generator / driverClassName).value
    val jdbcUrlValue                = (generator / jdbcUrl).value
    val jdbcUserValue               = (generator / jdbcUser).value
    val jdbcPasswordValue           = (generator / jdbcPassword).value
    val classNameMapperValue        = (generator / classNameMapper).value
    val propertyTypeNameMapperValue = (generator / propertyTypeNameMapper).value
    val tableNameFilterValue        = (generator / tableNameFilter).value
    val propertyNameMapperValue     = (generator / propertyNameMapper).value
    val schemaNameValue             = (generator / schemaName).value
    val templateDirectoryValue      = (generator / templateDirectory).value
    val templateNameMapperValue     = (generator / templateNameMapper).value
    val outputDirectoryMapperValue  = (generator / outputDirectoryMapper).value

    Def.task {
      val classLoader =
        if (enableManagedClassPathValue)
          ClasspathUtil.toLoader(
            managedClasspathData,
            ClasspathUtil.xsbtiLoader
          )
        else
          ClasspathUtil.xsbtiLoader

      Loan
        .using(
          getJdbcConnection(
            classLoader,
            driverClassNameValue,
            jdbcUrlValue,
            jdbcUserValue,
            jdbcPasswordValue
          )
        ) { conn =>
          implicit val ctx = GeneratorContext(
            logger,
            conn,
            classNameMapperValue,
            propertyTypeNameMapperValue,
            tableNameFilterValue,
            propertyNameMapperValue,
            schemaNameValue,
            templateDirectoryValue,
            templateNameMapperValue,
            outputDirectoryMapperValue
          )
          generateAll
        }.get
    }
  }

  /** テンプレートコンフィグレーションを生成する。
    *
    * @param templateDirectory テンプレートディレクトリ
    * @param logger            ロガー
    * @return テンプレートコンフィグレーション
    */
  private[generator] def createTemplateConfiguration(
      templateDirectory: File
  )(implicit logger: Logger): Try[freemarker.template.Configuration] = Try {
    logger.debug(s"createTemplateConfiguration($templateDirectory): start")
    var cfg: freemarker.template.Configuration = null
    try {
      cfg = new freemarker.template.Configuration(freemarker.template.Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS)
      cfg.setDirectoryForTemplateLoading(templateDirectory)
    } finally {
      logger.debug(s"createTemplateConfiguration: finished = $cfg")
    }
    cfg
  }

  /** テンプレートから複数のファイルを生成する。
    *
    * @param cfg       テンプレートコンフィグレーション
    * @param tableDesc [[TableDesc]]
    * @param ctx       [[GeneratorContext]]
    * @return TryにラップされたSeq[File]
    */
  private[generator] def generateFiles(cfg: freemarker.template.Configuration, tableDesc: Table)(implicit
      ctx: GeneratorContext
  ): Try[Seq[File]] = {
    implicit val logger = ctx.logger
    logger.debug(s"generateFiles($cfg, $tableDesc): start")
    val result = ctx
      .classNameMapper(tableDesc.getName)
      .foldLeft(Try(Seq.empty[File])) { (result, className) =>
        val outputTargetDirectory = ctx.outputDirectoryMapper(className)
        for {
          r    <- result
          file <- generateFile(cfg, tableDesc, className, outputTargetDirectory)
        } yield {
          r :+ file
        }
      }
    logger.debug(s"generateFiles: finished = $result")
    result
  }

  /** 出力先のファイルを生成する。
    *
    * @param outputDirectory 出力先ディレクトリ
    * @param className       クラス名
    * @return [[File]]
    */
  private[gen] def createFile(outputDirectory: File, className: String)(implicit logger: Logger): File = {
    logger.debug(s"createFile($outputDirectory, $className): start")
    val file = outputDirectory / (className + ".scala")
    logger.debug(s"createFile: finished = $file")
    file
  }

  /** プライマリーキーのためのコンテキストを生成する。
    *
    * @param propertyTypeNameMapper タイプマッパー
    * @param propertyNameMapper     プロパティマッパー
    * @param tableDesc              テーブルディスクリプション
    * @return コンテキスト
    */
  private[generator] def createPrimaryKeysContext(
      propertyTypeNameMapper: String => String,
      propertyNameMapper: String => String,
      tableDesc: Table
  )(implicit logger: Logger): Seq[Map[String, Any]] = {
    logger.debug(s"createPrimaryKeysContext($propertyTypeNameMapper, $propertyNameMapper, $tableDesc): start")
    val primaryKeys = tableDesc.primaryDescs.map { key =>
      val column           = tableDesc.columnDescs.find(_.columnName == key.columnName).get
      val propertyName     = propertyNameMapper(column.columnName)
      val propertyTypeName = propertyTypeNameMapper(column.typeName)
      Map[String, Any](
        "name"                      -> key.columnName,                      // deprecated
        "columnName"                -> key.columnName,
        "columnType"                -> column.typeName,                     // deprecated
        "columnTypeName"            -> column.typeName,
        "propertyName"              -> propertyName,
        "propertyType"              -> propertyTypeName,                    // deprecated
        "propertyTypeName"          -> propertyTypeName,
        "camelizeName"              -> StringUtil.camelize(key.columnName), // deprecated
        "camelizedColumnName"       -> StringUtil.camelize(key.columnName),
        "capitalizedColumnName"     -> StringUtil.capitalize(key.columnName),
        "capitalizedPropertyName"   -> StringUtil.capitalize(propertyName),
        "decamelizedPropertyName"   -> StringUtil.decamelize(propertyName),
        "decapitalizedPropertyName" -> StringUtil.decapitalize(propertyName),
        "autoIncrement"             -> key.autoIncrement,
        "nullable"                  -> column.nullable
      )
    }
    logger.debug(s"createPrimaryKeysContext: finished = $primaryKeys")
    primaryKeys
  }

  /** テンプレートからファイルを生成する。
    *
    * @param cfg             テンプレートコンフィグレーション
    * @param tableDesc       [[Table]]
    * @param className       クラス名
    * @param outputDirectory 出力先ディレクトリ
    * @param ctx             [[GeneratorContext]]
    * @return TryにラップされたFile
    */
  private[gen] def generateFile(
      cfg: freemarker.template.Configuration,
      tableDesc: Table,
      className: String,
      outputDirectory: File
  )(implicit ctx: GeneratorContext): Try[File] = {
    implicit val logger = ctx.logger
    logger.debug(s"generateFile($cfg, $tableDesc, $outputDirectory): start")
    val templateName = ctx.templateNameMapper(className)
    val template     = cfg.getTemplate(templateName)
    val file         = createFile(outputDirectory, className)
    ctx.logger.info(s"tableName = ${tableDesc.getName}, templateName = $templateName, generate file = $file")

    if (!outputDirectory.exists())
      IO.createDirectory(outputDirectory)

    val result = Loan.using(new FileWriter(file)) { writer =>
      val primaryKeys = createPrimaryKeysContext(ctx.typeNameMapper, ctx.propertyNameMapper, tableDesc)
      val columns     = createColumnsContext(ctx.typeNameMapper, ctx.propertyNameMapper, tableDesc)
      val context     = createContext(primaryKeys, columns, tableDesc.tableName, className)
      template.process(context, writer)
      writer.flush()
      Success(file)
    }
    logger.debug(s"generateFile: finished = $result")
    result
  }

  /** すべてのテーブルを指定してファイルを生成する。
    *
    * @param ctx [[GeneratorContext]]
    * @return 生成されたSeq[File]
    */
  private[gen] def generateAll(implicit ctx: GeneratorContext): Try[Seq[File]] = {
    implicit val logger = ctx.logger
    val options         = SchemaCrawlerOptionsBuilder.newSchemaCrawlerOptions()
    val catalog         = SchemaCrawlerUtility.getCatalog(ctx.connection, options)
    val result = for {
      cfg <- createTemplateConfiguration(ctx.templateDirectory)
      tables <- Try {
        ctx.schemaName
          .flatMap { schemaName =>
            catalog.getSchemas.asScala.find(_.getName == schemaName)
          }.map { schema =>
            catalog.getTables(schema).asScala.toVector
          }.getOrElse(Vector.empty)
      }
      files <- tables
        .filter { table =>
          ctx.tableNameFilter(table.getName)
        }
        .foldLeft(Try(Seq.empty[File])) { (result, table) =>
          for {
            r1 <- result
            r2 <- generateFiles(cfg, table)
          } yield r1 ++ r2
        }
    } yield files
    logger.debug(s"generateAll: finished = $result")
    result

  }

}
