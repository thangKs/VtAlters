/*
 * VtAlters - A plugin for summoning bosses via altars.
 * Copyright (C) 2025 thangks
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
