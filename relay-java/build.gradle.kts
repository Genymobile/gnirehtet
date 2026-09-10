plugins {
   application
}
 
application {
   mainClass.set("com.genymobile.gnirehtet.Main")
}
 
dependencies {
   implementation(fileTree("libs") {
      include("*.jar")
   })
 
   testImplementation("junit:junit:4.12")
}
