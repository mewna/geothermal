/*
 * This file is part of Hotspring.
 *
 * Hotspring is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Hotspring is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Hotspring.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mewna.api;

import lombok.Value;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author amy
 * @since 1/19/18.
 */
@Value
public class ApiContext {
    private final String guild;
    private final String channel;
    private final String userId;
    
    public static ApiContext fromContext(final JSONObject ctx) {
        try {
            return new ApiContext(ctx.getString("guild_id"), ctx.getString("channel_id"), ctx.getString("user_id"));
        } catch(JSONException e) {
            return new ApiContext(ctx.getString("guild"), ctx.getString("channel"), ctx.getString("userId"));
        }
    }
}
