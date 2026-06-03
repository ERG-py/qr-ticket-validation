package com.qrticket.validation.dto;

public enum ValidationResult {
    OK_TO_ENTER,   // билет валиден, можно пускать
    ALREADY_USED,  // уже был проход по этому билету
    NOT_FOUND,     // такого билета нет
    INVALID        // отменён / просрочен / вне дат
}