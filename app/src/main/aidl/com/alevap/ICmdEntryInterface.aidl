package com.alevap;

// This interface is used by utility on termux side.
interface ICmdEntryInterface {
    ParcelFileDescriptor getXConnection();
    ParcelFileDescriptor getLogcatOutput();
}