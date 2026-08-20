#!/usr/bin/env node

import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { open, readFile, stat } from "node:fs/promises";
import { basename } from "node:path";

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
  return value;
}

async function jsonRequest(url, options, attempts = 1) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(url, options);
      const text = await response.text();
      let body = {};
      if (text) {
        try { body = JSON.parse(text); }
        catch { throw new Error(`${options.method ?? "GET"} ${url.pathname} returned non-JSON HTTP ${response.status}`); }
      }
      if (!response.ok) throw new Error(`${options.method ?? "GET"} ${url.pathname} failed with HTTP ${response.status}: ${body.error ?? "unknown error"}`);
      return body;
    } catch (error) {
      lastError = error;
      if (attempt < attempts) await new Promise((resolve) => setTimeout(resolve, 1_000 * attempt));
    }
  }
  throw lastError;
}

async function hashFile(path) {
  const hash = createHash("sha256");
  for await (const chunk of createReadStream(path)) hash.update(chunk);
  return hash.digest("hex");
}

const baseUrl = new URL(required("UPDATE_LEDGER_BASE_URL"));
const project = required("UPDATE_LEDGER_PROJECT");
const apiKey = required("UPDATE_LEDGER_API_KEY");
const filePath = required("UPDATE_FILE");
const version = required("UPDATE_VERSION");
const build = required("UPDATE_BUILD");
const title = required("UPDATE_TITLE");
const notesPath = required("UPDATE_NOTES_FILE");
const channel = process.env.UPDATE_CHANNEL?.trim() || "stable";
const minVersionCode = Number(process.env.UPDATE_MIN_VERSION_CODE?.trim() || "0");
const sourceRevision = required("UPDATE_SOURCE_REVISION").toLowerCase();
const expectedSha256 = required("UPDATE_SHA256").toLowerCase();
const fileName = basename(filePath);
const fileInfo = await stat(filePath);
const actualSha256 = await hashFile(filePath);

if (!/^\d+$/.test(build)) throw new Error("UPDATE_BUILD must be numeric");
if (!/^[a-f0-9]{40}$/.test(sourceRevision)) throw new Error("UPDATE_SOURCE_REVISION must be a full Git commit SHA");
if (!Number.isSafeInteger(minVersionCode) || minVersionCode < 0) throw new Error("UPDATE_MIN_VERSION_CODE must be a non-negative integer");
if (!/^[a-f0-9]{64}$/.test(expectedSha256) || actualSha256 !== expectedSha256) {
  throw new Error("UPDATE_SHA256 does not match the artifact bytes");
}

const headers = {
  Authorization: `Bearer ${apiKey}`,
  "content-type": "application/json",
};
const initUrl = new URL(`/api/v1/artifacts/${encodeURIComponent(project)}`, baseUrl);
const upload = await jsonRequest(initUrl, {
  method: "POST",
  headers,
  body: JSON.stringify({
    fileName,
    version,
    build,
    channel,
    contentType: "application/vnd.android.package-archive",
    sha256: actualSha256,
    bytes: fileInfo.size,
  }),
});
if (!upload.uploadId || !upload.storageKey || !upload.downloadUrl) throw new Error("Update Ledger returned an incomplete upload session");

const partSize = Number(upload.partSize);
if (!Number.isSafeInteger(partSize) || partSize < 5 * 1024 * 1024) throw new Error("Update Ledger returned an invalid part size");

const parts = [];
const file = await open(filePath, "r");
try {
  for (let offset = 0, partNumber = 1; offset < fileInfo.size; offset += partSize, partNumber += 1) {
    const length = Math.min(partSize, fileInfo.size - offset);
    const buffer = Buffer.allocUnsafe(length);
    const { bytesRead } = await file.read(buffer, 0, length, offset);
    if (bytesRead !== length) throw new Error(`Short local read for upload part ${partNumber}`);
    const partUrl = new URL(`/api/v1/artifacts/${encodeURIComponent(project)}/parts/${partNumber}`, baseUrl);
    partUrl.searchParams.set("uploadId", upload.uploadId);
    partUrl.searchParams.set("storageKey", upload.storageKey);
    const part = await jsonRequest(partUrl, {
      method: "PUT",
      headers: { Authorization: `Bearer ${apiKey}`, "content-type": "application/octet-stream" },
      body: buffer,
    }, 3);
    if (!part.etag || Number(part.partNumber) !== partNumber) throw new Error(`Update Ledger returned an invalid receipt for part ${partNumber}`);
    parts.push({ partNumber, etag: part.etag });
    console.log(`Uploaded Update Ledger part ${partNumber}/${Math.ceil(fileInfo.size / partSize)}`);
  }
} finally {
  await file.close();
}

const completeUrl = new URL(`/api/v1/artifacts/${encodeURIComponent(project)}/complete`, baseUrl);
await jsonRequest(completeUrl, {
  method: "POST",
  headers,
  body: JSON.stringify({ uploadId: upload.uploadId, storageKey: upload.storageKey, parts }),
});

const notes = await readFile(notesPath, "utf8");
const releaseUrl = new URL(`/api/v1/releases/${encodeURIComponent(project)}`, baseUrl);
const published = await jsonRequest(releaseUrl, {
  method: "POST",
  headers,
  body: JSON.stringify({
    version,
    build,
    channel,
    title,
    notes,
    assetName: fileName,
    sha256: actualSha256,
    fileSize: fileInfo.size,
    minVersionCode,
    sourceRevision,
    storageKey: upload.storageKey,
    downloadUrl: upload.downloadUrl,
  }),
});

console.log(`Published Update Ledger release ${published.release?.version ?? version} build ${published.release?.build ?? build}`);
console.log(`JSON feed: ${published.updateUrl}`);
console.log(`Sparkle appcast: ${published.appcastUrl}`);
