package com.ph.typemath

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText


class MathTypeService : AccessibilityService() {
    private val tag = "MathTypeService"
    private val converter = StringConverter()
    private var afterChangeStringLen = -1
    private var beforeChangeString = ""
    //private var afterChangeString  = ""
    //private var afterChangeCursor  = -1
    private var justEdit = false

    private val prefsName = "TypeMathPrefsFile"

    override fun onInterrupt() {
        Log.e(tag, "onInterrupt: something went wrong")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // TODO : only edit if user is typing and not deleting
        // TODO : only undo if user is deleting the last char of edited string
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED){
            try {
                val className = Class.forName(event.className.toString())
                if (EditText::class.java.isAssignableFrom(className)) {
                    // EditText is detected
                    val nodeInfo: AccessibilityNodeInfo? = event.source
                    nodeInfo?.refresh()
                    val nodeString = nodeInfo?.text.toString()

                    // Split string by cursor
                    val tryCursorPos = nodeInfo?.textSelectionEnd
                    val cursorPos    = (
                            if (tryCursorPos != -1) tryCursorPos else nodeString.length) ?: 0
                    val headStr      = nodeString.substring(0, cursorPos)
                    val tailStr      = nodeString.substring(cursorPos)
                    Log.i(tag, "headStr: \"$headStr\" ; tailStr: \"$tailStr\"")

                    if(justEdit && nodeString.length == afterChangeStringLen - 1){
                        // User delete last char right after edit
                        val bundle = Bundle()
                        bundle.putString(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            beforeChangeString
                        )
                        nodeInfo?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                        justEdit = false
                    }
                    else if(headStr.isNotEmpty() && headStr.last() == ' '){
                        // Press space

                        // get values from sharedPreferences
                        val sh : SharedPreferences = getSharedPreferences(prefsName, MODE_PRIVATE)
                        val initStr          : String? = sh.getString("initString", ".")
                        val endStr           : String? = sh.getString("endString", ".")
                        val latexMode        : Boolean = sh.getBoolean("latexMode", false)
                        val useAdditionalSym : Boolean = sh.getBoolean("useAdditionalSymbols", false)
                        val keepSpace        : Boolean = sh.getBoolean("keepSpace", false)
                        val useDiacritics = false

                        if(initStr != null && endStr != null){
                            //do conversion only if initStr and endStr is not null

                            val toConvertStr = headStr.substring(0, headStr.length-1)
                            if(converter.isValidFormat(toConvertStr, initStr, endStr)){
                                //if string is valid

//                                Log.i(tag, "initStr: \"$initStr\" ; endStr: \"$endStr\"")
                                val converted = converter.evalString(
                                    toConvertStr, initStr, endStr,
                                    useAdditionalSym, useDiacritics,
                                    latexMode, keepSpace
                                )
                                val newCursorPos = cursorPos - 1 +
                                        (converted.length - toConvertStr.length)

                                val strBundle = Bundle()
                                strBundle.putString(
                                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                    converted + tailStr
                                )
                                nodeInfo?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, strBundle)

                                val cursorBundle = Bundle()
                                cursorBundle.putInt(
                                    AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursorPos
                                )
                                cursorBundle.putInt(
                                    AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursorPos
                                )
                                nodeInfo?.performAction(
                                    AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorBundle
                                )

                                afterChangeStringLen = converted.length
                                beforeChangeString = nodeString
                                justEdit = true
                            }
                        }
                    } else {
                        justEdit = false
                    }
                }
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }

        this.serviceInfo = info
        Log.i(tag, "onServiceConnected")
    }
}