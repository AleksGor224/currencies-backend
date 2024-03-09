package com.exchange.repository.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "configuration")
public class Configuration {

    @Id
    private String key;
    private String value;
}
