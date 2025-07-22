package com.example.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LogEntry {
    public String id;
    public String type;
    public Actor actor;

    public LogEntry() {
        // Default constructor for deserialization
    }
    public LogEntry(String id, String type, Actor actor) {
        this.id = id;
        this.type = type;
        this.actor = actor;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Actor getActor() {
        return actor;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setActor(Actor actor) {
        this.actor = actor;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Actor {
        public String login;

        public Actor() {}

        public Actor(String login) {
            this.login = login;
        }

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
        }

        @Override
        public String toString() {
            return login;
        }
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", actor=" + actor +
                '}';
    }
}
