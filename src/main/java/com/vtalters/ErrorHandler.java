/*
 * VtAlters - A plugin for summoning bosses via altars.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the LICENSE file in the root of this project for more information.
 */

package com.vtalters;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ErrorHandler {

    private final VtAlters plugin;
    private final Set<String> loggedErrorMessages = new HashSet<>();
    private final Set<String> errorReasons = new HashSet<>();

    public ErrorHandler(VtAlters plugin) {
        this.plugin = plugin;
    }

    public void logError(String message, String reason) {
        if (loggedErrorMessages.add(message)) {
            plugin.getLogger().severe(message);
        }
        errorReasons.add(reason);
    }

    public boolean hasErrors() {
        return !errorReasons.isEmpty();
    }

    public String getErrorReasons() {
        return String.join(", ", errorReasons);
    }

    public void clearErrors() {
        loggedErrorMessages.clear();
        errorReasons.clear();
    }
}
