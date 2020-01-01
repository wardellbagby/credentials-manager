package com.wardellbagby.credentialmanager

import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javafx.event.EventHandler
import javafx.scene.control.Alert.AlertType.ERROR
import javafx.scene.control.PasswordField
import javafx.scene.control.TableView
import javafx.scene.control.TextField
import javafx.scene.layout.Priority.ALWAYS
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tornadofx.App
import tornadofx.SmartResize
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
import java.io.File
import java.net.URLClassLoader
import java.util.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Modifier.*
import javax.tools.JavaCompiler
import javax.tools.ToolProvider
import kotlin.error

/**
 * TornadoFX (JavaFX Kotlin API) application.
 */
class CredentialsApp : App(primaryView = CredentialsView::class) {
    override fun start(stage: Stage) {
        super.start(stage)
        stage.width = 800.0
        stage.height = 600.0
    }
}

/**
 * Represents a stored credential.
 */
data class Credentials(val username: String, val password: String)

/**
 * Shows a screen where users can enter a new set of credentials and see a list of all currently entered
 * credentials.
 */
class CredentialsView : View("Credentials Manager"), CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private companion object {
        private const val credentialsPackage = "com.wardellbagby.credentialsmanager.credentials"
        // This doesn't include the package.
        private val storedCredentialsSourceDirectory =
            File(System.getProperty("java.io.tmpdir")).resolve("javapasswordmanager/")
        // This DOES include the package.
        private val storedCredentialsCodeDirectory =
            storedCredentialsSourceDirectory.resolve(credentialsPackage.replace('.', File.separatorChar))
    }

    private val usernameField: TextField
    private val passwordField: PasswordField
    private val table: TableView<Credentials>

    private val credentials = mutableListOf<Credentials>().asObservable()

    override val root = vbox(spacing = 8.0) {
        padding = insets(16.0)
    }

    init {
        storedCredentialsCodeDirectory.mkdirs()

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
                        launch {
                            saveCredential(Credentials(usernameField.text, passwordField.text))
                            loadCredentials()
                        }
                    }
                }
            }
            table = tableview(credentials) {
                readonlyColumn("Username", Credentials::username)
                readonlyColumn("Password", Credentials::password)

                isEditable = false
                columnResizePolicy = SmartResize.POLICY
            }
        }

        launch {
            loadCredentials()
        }
    }

    /**
     * Loads saved credentials by iterating over all Java class files and loading them into the application.
     *
     * We use a [URLClassLoader] here (because afaik, there's no real FileClassLoader and this will work on files and
     * directories) and give it the directory that has the class files that were created with [saveCredential].
     *
     * The only kicker here is that the [URLClassLoader] needs for classes to have a proper package directory structure.
     * I.e., if a class is in the package `com.wardellbagby.example`, it needs to be in the directory structure
     * `com/wardellbagby/example/example.class`. So the directory we feed to the [URLClassLoader] is the "source"
     * directory, to use Gradle terms.
     *
     * Once we've created the [URLClassLoader] with the right directory, we iterate over all class files and use the
     * class loader to, well, load the class. Once the class is loaded, we create a new [Credentials] where the username is
     * the name of the class, and the password is the name of the first method.
     *
     * @see saveCredential
     */
    private suspend fun loadCredentials() = coroutineScope {
        credentials.clear()

        val classLoader = URLClassLoader(arrayOf(storedCredentialsSourceDirectory.toURI().toURL()))

        credentials.addAll(
            storedCredentialsCodeDirectory.walk()
                .filter { it.isFile && it.extension == "class" }
                .map { classFile ->
                    val clazz = classLoader.loadClass(
                        "$credentialsPackage.${classFile.nameWithoutExtension}"
                    )
                    Credentials(clazz.simpleName, clazz.methods[0].name)
                })
    }

    /**
     * Saves a credential to a Java class file.
     *
     * First, we'll create the Java source (using JavaPoet). The name of the source file will be the username that was
     * entered. The source file will also have a single class inside of it; the name of that class will be the username
     * as well. The class will have a single public static method (not that it matters too much) where the name of the
     * method is the password the user entered.
     *
     * Then, using the system's [JavaCompiler], we'll compile that source into an actual *.class file, stored right next
     * to the source file we created earlier.
     */
    private suspend fun saveCredential(credentials: Credentials) = coroutineScope {
        with(credentials) {
            val passwordMethod = MethodSpec.methodBuilder(password)
                .addModifiers(FINAL, PUBLIC, STATIC)
                .returns(TypeName.VOID)
                .build()
            val usernameClass = TypeSpec.classBuilder(username)
                .addModifiers(PUBLIC, FINAL)
                .addMethod(passwordMethod)
                .build()

            val javaFileSource = JavaFile.builder(credentialsPackage, usernameClass).build()
            javaFileSource.writeTo(storedCredentialsSourceDirectory)


            val javaCompiler = ToolProvider.getSystemJavaCompiler()
            val fileManager = javaCompiler.getStandardFileManager(null, null, null)
            val filesToCompile = fileManager.getJavaFileObjectsFromFiles(
                listOf(storedCredentialsCodeDirectory.resolve("$username.java"))
            )
            storedCredentialsCodeDirectory
                .resolve("$username.class")
                .apply { createNewFile() }
                .writer()
                .use { writer ->
                    val task = javaCompiler.getTask(
                        writer,
                        fileManager,
                        {
                            /* This is a DiagnosticListener, that will be invoked if there are any issues compiling.
                            If there are, we'll just crash the app. */
                            error(it.getMessage(Locale.getDefault()))
                        },
                        null,
                        null,
                        filesToCompile
                    )
                    check(task.call()) {
                        "Failed to compile."
                    }
                }

        }
    }
}

fun main() {
    launch<App>()
}
