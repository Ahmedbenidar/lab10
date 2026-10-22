package com.example.service;

/** Levee lorsqu'un creneau demande chevauche une reservation existante. */
public class ConflitReservationException extends RuntimeException {

    public ConflitReservationException(String message) {
        super(message);
    }
}
