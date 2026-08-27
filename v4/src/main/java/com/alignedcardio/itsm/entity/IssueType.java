package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "issue_type")
public class IssueType extends BaseEntity {

    @NotNull
    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "icon", length = 64)
    private String icon;

    @Column(name = "color", length = 32)
    private String color;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
