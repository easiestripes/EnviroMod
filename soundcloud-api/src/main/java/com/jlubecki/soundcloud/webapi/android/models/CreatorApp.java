/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Jacob Lubecki
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.jlubecki.soundcloud.webapi.android.models;

/**
 * Representation of the app that was used to publish a sound on SoundCloud.
 *
 * @see <a href="https://developers.soundcloud.com/docs/api/reference#created_with">"Created With"
 * Reference</a> and
 * <a href="https://developers.soundcloud.com/docs/api/reference#apps">Apps Reference</a>
 */
public class CreatorApp {

    /**
     * ID of app used to create a sound.
     */
    public String id;

    /**
     * API resource url.
     */
    public String uri;

    /**
     * SoundCloud url to user.
     */
    public String permalink_url;

    /**
     * External url to app page.
     */
    public String external_url;

    /**
     * Creator's user name.
     */
    public String creator;
}