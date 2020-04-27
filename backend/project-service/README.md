# Projects

The projects feature allow for collaboration between different users across the
entire UCloud platform.

This project establishes the core abstractions for projects and establishes an
event stream for receiving updates about changes. Other services extend the
projects feature and subscribe to these changes to create the full project
feature.

## Interactions with other features

- [Project Repositories](../project-repository-service/README.md): Learn about
  how projects interact with the storage of UCloud.

## Definition

A project in UCloud is a collection of `members` which is uniquely identified
by an `id`. All `members` are [users](../auth-service) identified by their
`username` and have exactly one `role`. A user always has exactly one `role`.
Each project has exactly one principal investigator (`PI`). The `PI` is
responsible for managing the project, including adding and removing users.

| Role           | Notes                                                                                              |
|----------------|----------------------------------------------------------------------------------------------------|
| `PI`           | The primary point of contact for projects. All projects have exactly one PI.                       |
| `ADMIN`        | Administrators are allowed to perform some project management. A project can have multiple admins. |
| `USER`         | Has no special privileges.                                                                         |

**Table:** The possible roles of a project and their privileges within project
*management.

A project can be updated by adding/removing/changing any of its `members`.
Such an update will trigger a new message on the event stream. A project can
also be deleted, this will trigger a cleanup of associated resources (such as
files).

A project is sub-divided into groups:

![](wiki/structure.png)

Each project may have 0 or more groups. The groups can have 0 or more members.
A group belongs to exactly one project and the members of a group can only
be from the project it belongs to.
