# Credential Manager - The Best (Worst) App You Definitely (Don't) Need!

[![forthebadge](https://forthebadge.com/images/badges/approved-by-veridian-dynamics.svg)](https://forthebadge.com)
[![forthebadge](https://forthebadge.com/images/badges/you-didnt-ask-for-this.svg)](https://forthebadge.com)


Have you ever needed a credential manager, but all the other managers out there care about silly stuff like security and encryption? You know better, don't you reader? You know that the best, most secure way to store credentials are as Java class files!

Well, do I have the app for you!

## What does it do?

This wonderful app saves your credentials! You knew that already, didn't you?

### But _how_ does it do it? You said something about Java class files?

Yeah! It stores them as [Java .class files!](https://en.wikipedia.org/wiki/Java_class_file) 

### ...

Okay, I'll go more in depth here.

_Other_ credential managers might use a database of sorts. Probably encrypts it or something fancy like that.

Not here. We're simple. You give us some credentials? We're going to create some new Java source on the fly (thank you [JavaPoet!](https://github.com/square/javapoet)), compile it, and load the resulting compiled class back into the app.

Your username is the name of the Java class. Your password is the name of a single method inside of that class.  

### Seriously?

Seriously. 

### Is that secure?

Definitely (not)! There are definitely no ways to decompile a Java class file. Everyone knows that. And there's definitely no way someone else could write something to read a Java class file! That's just silly.

### Okay...but why?

See [this Twitter thread](https://twitter.com/wardellbagby/status/1211706100233293824?s=20)

### This is a joke, right?

Yes. Please don't actually use this.