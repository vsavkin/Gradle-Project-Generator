import groovy.text.SimpleTemplateEngine

abstract class Question {
	String question
	String defaultChoice = ""
	String key

	String acceptedAnswer(String answer) {
		answer = answer.trim() ?: defaultChoice
		assert validateAnswer(answer), 'Invalid answer'
		answer
	}

	abstract List getChoices()

	abstract boolean validateAnswer(String a)
}

class YesNoQuestion extends Question{
	boolean validateAnswer(String a) {
		a in ['yes', 'no', 'y', 'n']
	}

	List getChoices() {
		['yes', 'no']
	}
}

class MultipleChoiceQuestion extends Question{
	List choices

	boolean validateAnswer(String a) {
		a in choices
	}

	List getChoices() {
		choices
	}
}

class UserInputQuestion extends Question{
	List getChoices() {
		[]
	}

	boolean validateAnswer(String a) {
		true
	}
}

class Questions {
	List<Question> questions = []

	void addQuestion(Question q) {
		questions << q
	}

	Map run() {
		def options = [:]
		def readLine = System.in.newReader().&readLine
		questions.eachWithIndex {q, i->
			println "${i + 1}) ${q.question}"
			if (q.choices) {
				println "options: ${q.choices.join(", ")}"
			}
			if (q.defaultChoice) {
				println "default: ${q.defaultChoice}"
			}
			def answer = q.acceptedAnswer(readLine())
			options[q.key] = answer
			println ""
		}
		options
	}
}


class Generator {
	def generate(Map props){
		def template = '''
			apply plugin: 'groovy'
			apply plugin: 'idea'
			apply plugin: 'eclipse

			group = '${group}'
			version = '${version}'


			repositories {
				mavenCentral()
			}

			dependencies {
				groovy group: 'org.codehaus.groovy', name: 'groovy', version: '1.7.5'
			}


			//Uploading to Sonatype.
			//Dont't need for building the library
			apply plugin: de.huxhorn.gradle.pgp.PgpPlugin
			apply plugin: 'maven'

			buildscript {
				repositories {
					mavenCentral()
				}
				dependencies {
					classpath 'de.huxhorn.gradle:de.huxhorn.gradle.pgp-plugin:0.0.3'
				}
			}

			pgp {
				secretKeyRingFile = new File('${pgpFile}')
				keyId = ${pgpKeyId}
				password = ${pgpPassword}
			}

			task sourcesJar(type: Jar, dependsOn:classes) {
				 classifier = 'sources'
				 from sourceSets.main.allSource
			}

			task javadocJar(type: Jar, dependsOn:javadoc) {
				 classifier = 'javadoc'
				 from javadoc.destinationDir
			}

			artifacts {
				 archives sourcesJar
				 archives javadocJar
			}

			publishToSonatype {
				repositories.mavenDeployer {
					configuration = configurations.archives

					repository(url: "http://oss.sonatype.org/service/local/staging/deploy/maven2/") {
						authentication(userName: ${sonatypeUsername}, password: ${sonatypePassword})
					}

					pom.project {
						name '${projectName}'
						packaging 'jar'
						description '${projectDescription}'
						url '${projectUrl}'

						scm {
							url 'scm:git:file://${pathToFolder}'
							connection 'scm:git:file://${pathToFolder}'
						}

						licenses {
							license {
								name 'Apache2 License'
								distribution 'repo'
							}
						}

						developers {
							developer {
								id '${userName}'
								name '${userName}'
								email '${userEmail}'
								url '${userSite}'
							}
						}
					}
				}
			}
		'''.stripIndent()

		def engine = new SimpleTemplateEngine()
		engine.createTemplate(template).make(props).toString()
	}
}



class Application {

	Map askQuestions(){
		def q = new Questions()

		q.addQuestion new UserInputQuestion(question: 'Project name', key: 'projectName')
		q.addQuestion new UserInputQuestion(question: 'Project description', key: 'projectDescription')
		q.addQuestion new UserInputQuestion(question: 'Your github repository for this project', key: 'projectUrl')

		q.addQuestion new UserInputQuestion(question: 'GroupId of your project', key: 'group')
		q.addQuestion new UserInputQuestion(question: 'Version of your project', key: 'version', defaultChoice: '1.0')

		q.addQuestion new UserInputQuestion(question: 'Path to your PGP file', key: 'pgpFile')
		q.addQuestion new UserInputQuestion(question: 'Property name in your gradle.properties defining a PGP key id', key: 'pgpKeyId')
		q.addQuestion new UserInputQuestion(question: 'Property name in your gradle.properties defining a PGP key password', key: 'pgpPassword')

		q.addQuestion new UserInputQuestion(question: 'Property name in your gradle.properties defining sonatype username', key: 'sonatypeUsername')
		q.addQuestion new UserInputQuestion(question: 'Property name in your gradle.properties defining sonatype password', key: 'sonatypePassword')

		q.addQuestion new UserInputQuestion(question: 'Your name', key: 'userName')
		q.addQuestion new UserInputQuestion(question: 'Your email', key: 'userEmail')
		q.addQuestion new UserInputQuestion(question: 'Your site', key: 'userSite')

		def props = q.run()
		props.pathToFolder = new File('.').absolutePath
		props
	}

	void createBuildGradle(Map opts){
		def g = new Generator()
		def content = g.generate(opts)

		new File('build.gradle').withWriter {
			it << content
		}
	}

	void createFolders(String group){
		def f = group.replaceAll(/\./, '/')
		new File("src/main/${f}").mkdirs()
		new File("src/test/${f}").mkdirs()
	}

	void run(){
		println """
		Generating a Gradle Project...

		First of all:
		1) Generate and distribute a PGP file [link: http://www.sonatype.com/people/2010/01/how-to-generate-pgp-signatures-with-maven/]
		2) Sign up on http://issues.sonatype.org, Create an issue for your new project [link: 'how to']
		3) Create a file: HOME_DIR/.gradle/gradle.properties
		4) Add 4 properties to gradle.properties: your PGP key id, PGP key password, sonatype username and sonatype password
		5) Create a repository for your project on GitHub

		And answer a few questions:
		""".stripIndent()

		Map props = askQuestions()
		createBuildGradle props
		createFolders props.group
	}
}

def app = new Application()
app.run()
