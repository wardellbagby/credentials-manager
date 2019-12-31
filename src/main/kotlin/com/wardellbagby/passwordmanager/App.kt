package com.wardellbagby.passwordmanager

import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import com.sun.tools.javac.api.JavacTool
import javafx.event.EventHandler
import javafx.scene.control.Alert.AlertType.ERROR
import javafx.scene.control.PasswordField
import javafx.scene.control.TableView
import javafx.scene.control.TextField
import javafx.scene.layout.Priority.ALWAYS
import javafx.stage.Stage
import tornadofx.App
import tornadofx.View
import tornadofx.alert
import tornadofx.asObservable
import tornadofx.button
import tornadofx.insets
import tornadofx.label
import tornadofx.launch
import tornadofx.passwordfield
import tornadofx.readonlyColumn
import tornadofx.tableview
import tornadofx.textfield
import tornadofx.useMaxWidth
import tornadofx.vbox
import tornadofx.vboxConstraints
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Files.isRegularFile
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.Locale
import javax.lang.model.SourceVersion
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC
import kotlin.streams.toList

class App : App(primaryView = PasswordsView::class) {
    override fun start(stage: Stage) {
        super.start(stage)
        stage.width = 800.0
        stage.height = 600.0
    }
}

data class Login(val username: String, val password: String)

class PasswordsView : View("Password Manager") {
    private companion object {
        private val loginsDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "javapasswordmanager/")
        private val loginsCompiledCodeDirectory = loginsDirectory.resolve(
            Paths.get(
                "com",
                "wardellbagby",
                "passwordmanager",
                "passwords"
            )
        )
    }

    private val usernameField: TextField
    private val passwordField: PasswordField
    private val table: TableView<Login>

    private val logins = mutableListOf<Login>().asObservable()

    override val root = vbox(spacing = 8.0) {
        padding = insets(16.0)
    }

    init {
        Files.createDirectories(loginsCompiledCodeDirectory)

        with(root) {
            label("Username:")
            usernameField = textfield {
                vboxConstraints { marginBottom = 8.0 }
            }
            label("Password:")
            passwordField = passwordfield()
            button("Submit") {
                vboxConstraints {
                    marginBottom = 20.0
                    vGrow = ALWAYS
                }
                useMaxWidth = true
                onAction = EventHandler {
                    val password = passwordField.text
                    val username = usernameField.text

                    if (!SourceVersion.isName(password) || !SourceVersion.isName(username)) {
                        alert(
                            ERROR, "Invalid Username/Password",
                            """
                                |Your username and/or password is invalid. Valid usernames and passwords must follow these rules:
                                
                                | - The only allowed characters for identifiers are all alphanumeric characters([A-Z],[a-z],[0-9]), "${'$'}"(dollar sign) and "_" (underscore). For example "password@" is not valid as it contains the '@' special character.
                                | - Usernames/Passwords should not start with digits([0-9]). For example “123password” is a not a valid username or password.
                                | - Reserved Words in the Java language (unrelated...) can’t be used as a username or password. For example "while" is invalid as "while" is a reserved word. There are 53 reserved words in Java.                                
                            """.trimMargin().trimIndent()
                        )
                    } else {
                        saveLogin(Login(usernameField.text, passwordField.text))
                        loadLogins()
                    }
                }
            }
            table = tableview(logins) {
                isEditable = false
                readonlyColumn("Username", Login::username)
                readonlyColumn("Password", Login::password)
            }
        }

        loadLogins()
    }

    private fun loadLogins() {
        logins.clear()

        val classLoader = URLClassLoader(arrayOf(loginsDirectory.toUri().toURL()))
        logins.addAll(
            Files.walk(loginsCompiledCodeDirectory)
                .toList()
                .filter { isRegularFile(it) && it.toString().endsWith(".class") }
                .map { usernameClass ->
                    val clazz = classLoader.loadClass(
                        "com.wardellbagby.passwordmanager.passwords.${usernameClass.fileName.toString().removeSuffix(".class")}"
                    )
                    Login(clazz.simpleName, clazz.methods[0].name)
                })
    }

    private fun saveLogin(login: Login): Unit = with(login) {
        val passwordMethod = MethodSpec.methodBuilder(password)
            .addModifiers(FINAL, PUBLIC, STATIC)
            .returns(TypeName.VOID)
            .build()
        val usernameClass = TypeSpec.classBuilder(username)
            .addModifiers(PUBLIC, FINAL)
            .addMethod(passwordMethod)
            .build()

        val javaFileSource = JavaFile.builder("com.wardellbagby.passwordmanager.passwords", usernameClass).build()
        javaFileSource.writeTo(loginsDirectory)

        val javac = JavacTool.create()
        val fileManager = javac.getStandardFileManager(null, null, null)
        val filesToCompile = fileManager.getJavaFileObjectsFromFiles(
            listOf(loginsCompiledCodeDirectory.resolve("$username.java").toFile())
        )
        Files.newBufferedWriter(loginsCompiledCodeDirectory.resolve("$username.class"), WRITE, CREATE).use { writer ->
            val task = javac.getTask(
                writer,
                fileManager,
                { System.err.println(it.getMessage(Locale.US)) },
                null,
                null,
                filesToCompile
            )
            check(task.call())
        }

    }
}

fun main() {
    launch<App>()
}
