# CRUD Wizardry Generator

CRUD Wizardry Generator is an IntelliJ IDEA plugin designed to streamline Spring Boot development by automating the generation of boilerplate CRUD components.

## Features

- **Entity Generation**: Create JPA Entities with Lombok annotations via a simple dialog.
- **Repository Generation**: Generate Spring Data JPA Repositories for existing entities.
- **Service Layer Generation**: Generate Service interfaces and implementations.
- **Controller Generation**: Generate REST Controllers with standard CRUD endpoints.
- **DTO Generation**: Generate Data Transfer Objects from entities.

## Installation

1. Download the latest plugin build.
2. In IntelliJ IDEA, go to `Settings` > `Plugins`.
3. Click the gear icon and select `Install Plugin from Disk...`.
4. Select the downloaded ZIP file.
5. Restart IntelliJ IDEA.

## How to Use

### Generating an Entity
1. Right-click on a package in the Project view.
2. Select `New` > `Generate Entity`.
3. Fill in the class name and fields in the dialog.
4. Click OK to generate the class.

### Generating CRUD Components
1. Right-click on an existing Entity class file.
2. Navigate to `Generate CRUD Components` (or use the `Generate` menu `Alt+Insert`).
3. Select the component you want to generate (Controller, Service, Repository, or DTO).

## Requirements

- IntelliJ IDEA 2023.1 or later.
- Java 17 or later.
- Spring Boot project structure.

## License

This project is licensed under the Apache License 2.0.

---
Developed by [BabsCron](https://www.babscrontekker.com)