package com.indie.catalog.domain.artist.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "label", uniqueConstraints = @UniqueConstraint(name = "uq_label_name", columnNames = "name"))
public class LabelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    protected LabelEntity() {}

    public LabelEntity(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
}
