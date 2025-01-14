package de.moekadu.tuner.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Parcelable
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import de.moekadu.tuner.R
import kotlinx.parcelize.Parcelize
import kotlin.math.*

class StringView(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
    : View(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet? = null) : this(
        context,
        attrs,
        R.attr.stringViewStyle
    )

    class StringInfo(val toneIndex: Int, val label: CharSequence?) {
        val layouts = Array<StaticLayout?>(3) { null }
    }

    enum class HighlightBy { StringIndex, ToneIndex, Off }

    private val stringPaint = arrayOf(
        Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        },
        Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        },
        Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
    )

    private val labelBackgroundPaint = arrayOf(
        Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = true
        },
        Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = true
        },
        Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    )

    private val labelPaint = arrayOf(
        TextPaint().apply {
            color = Color.GREEN
            textSize = 12f
            isAntiAlias = true
        },
        TextPaint().apply {
            color = Color.GREEN
            textSize = 12f
            isAntiAlias = true
        },
        TextPaint().apply {
            color = Color.RED
            textSize = 12f
            isAntiAlias = true
        }
    )

    private val framePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private var frameColor = Color.BLACK
    private var frameColorOnTouch = Color.RED
    private var frameCornerRadius = 0f

    private var labelBackgroundPadding = 2f
    private var labelSpacing = 2f

    private var labelWidth = 0f
    private var labelWidthExpanded = 0f ///< Expanded label with so that it fills the columns
    private var labelHeight = 0f

    private val strings = ArrayList<StringInfo>()

//    private var activeToneIndex: Int = NO_ACTIVE_TONE_INDEX
//        set(value) {
//            if (value != field) {
//                field = value
//                updateActiveStringIndex(value)
//                if (field != NO_ACTIVE_TONE_INDEX && automaticScrollToHighlight)
//                    scrollToString(value, 200L)
//                else
//                    invalidate()
//            }
//        }

    var activeToneStyle: Int = 1
        set(value) {
            if (value != field) {
                field = value
                anchorDrawable.setColors(labelBackgroundPaint[value].color, labelPaint[value].color)
                invalidate()
            }
        }

    private var highlightBy = HighlightBy.Off
    var highlightedStringIndex: Int = -1
        private set
    private var toneIndexForHighlighting = NO_ACTIVE_TONE_INDEX
    private var stringIndexForCenteringToneIndex = -1
    private var numHighlightedWithToneIndex = 0

    private var yOffset = 0f

    private val offsetAnimator = ValueAnimator().apply {
        addUpdateListener {
            val offset = it.animatedValue as Float
            yOffset = offset
//            Log.v("Tuner", "StringView.offsetAnimator: yOffset = $yOffset")
            updateStringPositionVariables(width, height)
            ViewCompat.postInvalidateOnAnimation(this@StringView)
        }
    }

    private val flingAnimation = FlingAnimation(FloatValueHolder()).apply {
        addUpdateListener { _, value, _ ->
            val distance = lastFlingValue - value
            lastFlingValue = value
            scrollDistance(distance)
        }
    }

    /// Temporary storage needed for fling animations
    var lastFlingValue = 0f

    /// Number of labels which can be placed next to each other
    private var numCols = 1

    /// The total horizontal space each label + equidistant spacing takes
    private var colWidth = 1f

    /// Vertical space for needed for each additional string
    private var rowHeight = 1f

    /// Current start index of string which is actually visible
    private var stringStartIndex = 0

    /// Current end index (index is included) of string which is actually visible
    private var stringEndIndex = 0

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent?): Boolean {
//            Log.v("Tuner", "PlotView: gestureListener.OnDown")
            flingAnimation.cancel()
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent?,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
//            Log.v("Tuner", "PlotView: gestureListener.OnScroll x=$distanceX, y=$distanceY")
            setManualControl()
            scrollDistance(distanceY)
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent?,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            lastFlingValue = 0f
            offsetAnimator.cancel()
            flingAnimation.cancel()
            flingAnimation.setStartValue(0f)
            flingAnimation.setStartVelocity(velocityY)
            flingAnimation.start()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent?): Boolean {
            if (e == null || stringClickedListener == null)
                return true

            val x = e.x
            val y = e.y
            val halfSizeX = 0.5f * labelWidthExpanded
            val halfSizeY = rowHeight
            var stringIndex = -1
            for (i in stringStartIndex..stringEndIndex) {
                val xPos = getStringDrawingPositionX(i)
                val yPos = getStringDrawingPositionY(i)
                if (x >= xPos - halfSizeX && x < xPos + halfSizeX
                    && y >= yPos - halfSizeY && y < yPos + halfSizeY
                ) {
                    stringIndex = i
                    break
                }
            }
//            Log.v("Tuner", "StringView..onSingleTapUp: toneIndex=$toneIndex")
            if (stringIndex >= 0) {
                stringClickedListener?.onStringClicked(stringIndex, strings[stringIndex].toneIndex)
            } else if (showAnchor && anchorDrawable.contains(x, y)) {
                stringClickedListener?.onAnchorClicked()
            } else if ((anchorDrawablePosition == 0 && x < paddingLeft) ||
                (anchorDrawablePosition == 1 && x > width - paddingRight)) {
//                Log.v("Tuner", "StringView: Center icon clicked, highlightBy=$highlightBy, automaticScrollToSelected=$automaticScrollToSelected")
                // select next note if we are in automatic scroll mode and highlight by toneIndex
                if (automaticScrollToSelected && highlightBy == HighlightBy.ToneIndex) {
                    stringIndexForCenteringToneIndex =
                        getNextCenteringStringIndexWithToneIndex(stringIndexForCenteringToneIndex, toneIndexForHighlighting)
//                    Log.v("Tuner", "StringView: Center icon clicked, centeringStringIndex=$stringIndexForCenteringToneIndex")
                }
                setAutomaticControl(200L)
            } else {
                stringClickedListener?.onBackgroundClicked()
            }
            //if (toneIndex == NO_ACTIVE_TONE_INDEX)
            //    setAutomaticControl()
            if (isSoundEffectsEnabled)
                playSoundEffect(android.view.SoundEffectConstants.CLICK)
            performClick()
            return true
        }
    }

    private val gestureDetector = GestureDetector(context, gestureListener)

    interface StringClickedListener {
        fun onStringClicked(stringIndex: Int, toneIndex: Int)
        fun onAnchorClicked()
        fun onBackgroundClicked()
    }

    var stringClickedListener: StringClickedListener? = null

    private var automaticScrollToSelected = true

    private var stringIndexInViewCenter = Float.MAX_VALUE

    private var touchManualControlDrawable: TouchControlDrawable
    private var anchorDrawable: TouchControlDrawable
    var showAnchor = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    private var anchorDrawablePosition = 1 // 0 -> left, 1 -> right

    private var scrollCenterDrawable: TouchControlDrawable
    private var scrollCenterDrawableRed: TouchControlDrawable
    //private var scrollUpDrawable: TouchControlDrawable
    //private var scrollDownDrawable: TouchControlDrawable

    @Parcelize
    private class SavedState(
        val highlightBy: HighlightBy,
        val highlightedStringIndex: Int,
        val toneIndexForHighlighting: Int,
        val stringIndexForCenteringToneIndex: Int,
        val activeToneStyle: Int,
        val stringIndexInViewCenter: Float, // for recomputing offset
        val automaticScrollToSelected: Boolean,
        val showAnchor: Boolean
    ) : Parcelable

    init {
        var touchDrawableId = R.drawable.ic_manual
        var touchManualControlDrawableWidth = 10f
        var touchDrawableBackgroundTint = Color.WHITE

        var anchorDrawableId = R.drawable.ic_anchor_inv_x
        var anchorDrawableWidth = 10f

        attrs?.let {
            val ta = context.obtainStyledAttributes(
                attrs,
                R.styleable.StringView,
                defStyleAttr,
                R.style.StringViewStyle
            )
            labelBackgroundPadding =
                ta.getDimension(R.styleable.StringView_labelPadding, labelBackgroundPadding)
            labelSpacing = ta.getDimension(R.styleable.StringView_labelSpacing, labelSpacing)

            labelPaint[0].textSize =
                ta.getDimension(R.styleable.StringView_labelTextSize, labelPaint[0].textSize)
            labelPaint[0].color =
                ta.getColor(R.styleable.StringView_labelTextColor, labelPaint[0].color)

            labelPaint[1].textSize =
                ta.getDimension(R.styleable.StringView_labelTextSize2, labelPaint[1].textSize)
            labelPaint[1].color =
                ta.getColor(R.styleable.StringView_labelTextColor2, labelPaint[1].color)

            labelPaint[2].textSize =
                ta.getDimension(R.styleable.StringView_labelTextSize3, labelPaint[2].textSize)
            labelPaint[2].color =
                ta.getColor(R.styleable.StringView_labelTextColor3, labelPaint[2].color)

            stringPaint[0].color =
                ta.getColor(R.styleable.StringView_stringColor, stringPaint[0].color)
            stringPaint[0].strokeWidth =
                ta.getDimension(R.styleable.StringView_stringLineWidth, stringPaint[0].strokeWidth)

            stringPaint[1].color =
                ta.getColor(R.styleable.StringView_stringColor2, stringPaint[1].color)
            stringPaint[1].strokeWidth =
                ta.getDimension(R.styleable.StringView_stringLineWidth2, stringPaint[1].strokeWidth)

            stringPaint[2].color =
                ta.getColor(R.styleable.StringView_stringColor3, stringPaint[2].color)
            stringPaint[2].strokeWidth =
                ta.getDimension(R.styleable.StringView_stringLineWidth3, stringPaint[2].strokeWidth)

            labelBackgroundPaint[0].color = stringPaint[0].color
            labelBackgroundPaint[1].color = stringPaint[1].color
            labelBackgroundPaint[2].color = stringPaint[2].color

            frameColor = ta.getColor(R.styleable.StringView_frameColor, frameColor)
            frameColorOnTouch =
                ta.getColor(R.styleable.StringView_frameColorOnTouch, frameColorOnTouch)
            framePaint.strokeWidth =
                ta.getDimension(R.styleable.StringView_frameStrokeWidth, framePaint.strokeWidth)
            framePaint.color = frameColor
            frameCornerRadius = ta.getDimension(R.styleable.StringView_frameCornerRadius, frameCornerRadius)

            touchDrawableId =
                ta.getResourceId(R.styleable.StringView_touchDrawable, touchDrawableId)
            touchManualControlDrawableWidth = ta.getDimension(
                R.styleable.StringView_touchDrawableWidth,
                touchManualControlDrawableWidth
            )
            touchDrawableBackgroundTint = ta.getColor(
                R.styleable.StringView_touchDrawableBackgroundTint,
                touchDrawableBackgroundTint
            )

            anchorDrawableId =
                ta.getResourceId(R.styleable.StringView_anchorDrawable, anchorDrawableId)
            anchorDrawableWidth =
                ta.getDimension(R.styleable.StringView_anchorDrawableWidth, anchorDrawableWidth)
            anchorDrawablePosition = ta.getInt(R.styleable.StringView_anchorDrawablePosition, 1)
            ta.recycle()
        }

        touchManualControlDrawable = TouchControlDrawable(
            context,
            frameColorOnTouch,
            touchDrawableBackgroundTint,
            touchDrawableId
        )
        touchManualControlDrawable.setSize(width = touchManualControlDrawableWidth)

        anchorDrawable = TouchControlDrawable(
            context,
            labelBackgroundPaint[activeToneStyle].color,
            labelPaint[activeToneStyle].color,
            anchorDrawableId
        )
        anchorDrawable.setSize(width = anchorDrawableWidth)

//        scrollUpDrawable = TouchControlDrawable(context, frameColor, null, R.drawable.ic_scroll_up)
//        scrollUpDrawable.setSize(width = 0.5f * anchorDrawableWidth)
//
//        scrollDownDrawable =
//            TouchControlDrawable(context, frameColor, null, R.drawable.ic_scroll_down)
//        scrollDownDrawable.setSize(width = 0.5f * anchorDrawableWidth)scrollUpDrawable = TouchControlDrawable(context, frameColor, null, R.drawable.ic_scroll_up)
//        scrollUpDrawable.setSize(width = 0.5f * anchorDrawableWidth)

        scrollCenterDrawable = TouchControlDrawable(context, null, null, R.drawable.ic_scroll_center)
        scrollCenterDrawable.setSize(width = 0.5f * anchorDrawableWidth)
        scrollCenterDrawableRed = TouchControlDrawable(context, null, null, R.drawable.ic_scroll_center_red)
        scrollCenterDrawableRed.setSize(width = 0.5f * anchorDrawableWidth)

    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
//        Log.v("Tuner", "StringView.onMeasure")
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        var proposedWidth = max(MeasureSpec.getSize(widthMeasureSpec), suggestedMinimumWidth)
        if (widthMode == MeasureSpec.UNSPECIFIED)
            proposedWidth = max(
                6 * ((labelWidth + labelSpacing) + labelSpacing + paddingLeft + paddingRight + 2 * framePaint.strokeWidth).toInt(),
                suggestedMinimumWidth
            )
        val w = resolveSize(proposedWidth, widthMeasureSpec)
        val desiredHeight = max(
            (paddingTop + getTotalStringHeight() + 2 * (labelSpacing + framePaint.strokeWidth) + paddingBottom).roundToInt() + 1,
            suggestedMinimumHeight
        )
        val h = resolveSize(desiredHeight, heightMeasureSpec)
//        Log.v("Tuner", "StringView.onMeasure: width=${MeasureSpec.toString(widthMeasureSpec)}, height=${MeasureSpec.toString(heightMeasureSpec)}, desiredHeight=$desiredHeight, resolvedHeight=$h")
        setMeasuredDimension(w, h)
        //super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val g = gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event) || g
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
//        Log.v("Tuner", "StringView.onDraw: onSizeChanged")
        updateStringPositionVariables(w, h) // for computing numcols etc.
        if (stringIndexInViewCenter != Float.MAX_VALUE) {
            yOffset = getYOffsetFromStringIndex(stringIndexInViewCenter)
//            Log.v("Tuner", "StringView.onDraw: onSizeChanged: stringIndexInViewCenter=$stringIndexInViewCenter, offset=$yOffset")
            stringIndexInViewCenter = Float.MAX_VALUE
        }
        if (automaticScrollToSelected)
            setAutomaticControl(0L)

        updateStringPositionVariables(w, h) // for computing minstringindex and maxstringindex
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onDraw(canvas: Canvas?) {
//        Log.v("Tuner", "StringView.onDraw: yOffset = $yOffset")
        if (canvas == null)
            return
//        canvas.drawRect(
//            paddingLeft.toFloat() + 0.5f * framePaint.strokeWidth,
//            paddingTop.toFloat() + 0.5f * framePaint.strokeWidth,
//            width - paddingRight.toFloat() - 0.5f * framePaint.strokeWidth,
//            height - paddingBottom.toFloat() - 0.5f * framePaint.strokeWidth,
//            framePaint
//        )
        canvas.drawRoundRect(
            paddingLeft.toFloat() + 0.5f * framePaint.strokeWidth,
            paddingTop.toFloat() + 0.5f * framePaint.strokeWidth,
            width - paddingRight.toFloat() - 0.5f * framePaint.strokeWidth,
            height - paddingBottom.toFloat() - 0.5f * framePaint.strokeWidth,
            frameCornerRadius, frameCornerRadius,
            framePaint
        )
        //canvas.save()
        //canvas.clipRect(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        canvas.clipRect(
            0,
            (paddingTop + framePaint.strokeWidth).toInt(),
            width,
            (height - paddingBottom - framePaint.strokeWidth).toInt()
        )
        var anchorYPos = NO_ANCHOR

        if (strings.size > 0) {
            for (i in stringStartIndex..stringEndIndex) {
                val xPos = getStringDrawingPositionX(i)
                val yPos = getStringDrawingPositionY(i)
                val highlight = (highlightBy == HighlightBy.StringIndex && i == highlightedStringIndex)
                        || (highlightBy == HighlightBy.ToneIndex && toneIndexForHighlighting == strings[i].toneIndex)
                drawString(
                    xPos,
                    yPos,
                    strings[i],
                    if (highlight) activeToneStyle else 0,
                    canvas
                )

                if (highlightBy == HighlightBy.StringIndex && i == highlightedStringIndex)
                    anchorYPos = yPos
            }
        }
//        if (!automaticScrollToHighlight) {
//            touchManualControlDrawable.drawToCanvas(
//                width - paddingRight.toFloat() - 0.5f * framePaint.strokeWidth + 1,
//                paddingTop.toFloat() + 0.5f * framePaint.strokeWidth - 1,
//                MarkAnchor.NorthEast,
//                canvas
//            )
//        }

        //canvas.restore()
        if (showAnchor) {
            val minYPos = paddingTop + 0.5f * anchorDrawable.height + framePaint.strokeWidth
            val maxYPos =
                height - paddingBottom - 0.5f * anchorDrawable.height - framePaint.strokeWidth

            if (anchorYPos == NO_ANCHOR) {
                anchorYPos = if (highlightedStringIndex < stringStartIndex)
                    minYPos
                else
                    maxYPos
            }
            anchorYPos = min(anchorYPos, maxYPos)
            anchorYPos = max(anchorYPos, minYPos)
            if (anchorDrawablePosition == 0) { // left
                anchorDrawable.drawToCanvas(
                    paddingLeft.toFloat() + framePaint.strokeWidth,
                    anchorYPos,
                    MarkAnchor.East, canvas
                )
            } else { // right
                anchorDrawable.drawToCanvas(
                    width - paddingRight.toFloat() - framePaint.strokeWidth,
                    anchorYPos,
                    MarkAnchor.West, canvas
                )
            }
        }

        var showScrollIcon = !automaticScrollToSelected && highlightBy == HighlightBy.ToneIndex && numHighlightedWithToneIndex > 0
        showScrollIcon = showScrollIcon || (!automaticScrollToSelected && highlightBy == HighlightBy.StringIndex && highlightedStringIndex in strings.indices)
        showScrollIcon = showScrollIcon || (highlightBy == HighlightBy.ToneIndex && numHighlightedWithToneIndex > 1)
        showScrollIcon = showScrollIcon && (computeOffsetMin() < computeOffsetMax())
        // TODO: show another icon, if we are in automatic scroll mode and have multiple notes with same tone index

        // show scrollToActive-icon either if we have an active note and are not centered
        // OR always if we have more than one highlighted note (since then we jump between the notes when clicking it)
        if (showScrollIcon) {
            // val yOffsetTarget = getYOffsetAutoScroll(activeStringIndex)
            var yPosition = 0.5f * (paddingTop + height - paddingBottom)
            if (showAnchor && anchorYPos != NO_ANCHOR && anchorYPos >= yPosition){ //draw above anchor
                yPosition = min(yPosition, anchorYPos - 0.5f * (anchorDrawable.height + scrollCenterDrawable.height) - framePaint.strokeWidth)
            } else if (showAnchor && anchorYPos != NO_ANCHOR && anchorYPos < yPosition) { //draw below anchor
                yPosition = max(yPosition, anchorYPos + 0.5f * (anchorDrawable.height + scrollCenterDrawable.height) + framePaint.strokeWidth)
            }

            val xPositionScrollDrawable = if (anchorDrawablePosition == 0) { // left
                paddingLeft.toFloat() + framePaint.strokeWidth - 0.5f * anchorDrawable.width
            } else { // right
                width - paddingRight.toFloat() - framePaint.strokeWidth + 0.5f * anchorDrawable.width
            }

            if (activeToneStyle == 1) {
                scrollCenterDrawable.drawToCanvas(
                    xPositionScrollDrawable,
                    yPosition,
                    MarkAnchor.Center, canvas
                )
            } else if (activeToneStyle == 2) {
                scrollCenterDrawableRed.drawToCanvas(
                    xPositionScrollDrawable,
                    yPosition,
                    MarkAnchor.Center, canvas
                )
            }


//            if (yOffsetTarget <= yOffset) {
//                scrollUpDrawable.drawToCanvas(
//                    xPositionScrollDrawable,
//                    paddingTop + framePaint.strokeWidth,
//                    MarkAnchor.North, canvas
//                )
//            }
//            if (yOffsetTarget >= yOffset) {
//                scrollDownDrawable.drawToCanvas(
//                    xPositionScrollDrawable,
//                    height - paddingBottom - framePaint.strokeWidth,
//                    MarkAnchor.South, canvas
//                )
//            }
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable("super state", super.onSaveInstanceState())

        val state = SavedState(
            highlightBy = highlightBy,
            highlightedStringIndex = highlightedStringIndex,
            toneIndexForHighlighting = toneIndexForHighlighting,
            stringIndexForCenteringToneIndex = stringIndexForCenteringToneIndex,
            activeToneStyle = activeToneStyle,
            stringIndexInViewCenter = getStringIndexAtYCenterAsFloat(),
            automaticScrollToSelected = automaticScrollToSelected,
            showAnchor = showAnchor
        )

        bundle.putParcelable("string view state", state)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
//        Log.v("Tuner", "StringView.onRestoreInstanceState")
        val superState = if (state is Bundle) {
            state.getParcelable<SavedState>("string view state")?.let { scrollViewState ->
                highlightBy = scrollViewState.highlightBy
                highlightedStringIndex = scrollViewState.highlightedStringIndex
                toneIndexForHighlighting = scrollViewState.toneIndexForHighlighting
                stringIndexForCenteringToneIndex = scrollViewState.stringIndexForCenteringToneIndex
                activeToneStyle = scrollViewState.activeToneStyle
                automaticScrollToSelected = scrollViewState.automaticScrollToSelected
                showAnchor = scrollViewState.showAnchor
//                Log.v("Tuner", "StringView.onRestoreInstanceState: stringIndexInViewCenter=${scrollViewState.stringIndexInViewCenter}, automaticScrollToSelected=$automaticScrollToSelected")
                stringIndexInViewCenter = if (automaticScrollToSelected) Float.MAX_VALUE else scrollViewState.stringIndexInViewCenter
            }
            state.getParcelable("super state")
        } else {
            state
        }
        super.onRestoreInstanceState(superState)
    }


    fun setStrings(toneIndices: IntArray, labels: (Int) -> CharSequence?) {
        strings.clear()
        var labelWidth = 0
        var labelHeight = 0

        for (toneIndex in toneIndices) {
            strings.add(StringInfo(toneIndex, labels(toneIndex)))
            strings.last().layouts[0] = buildLabelLayout(strings.last().label, 0)
            strings.last().layouts[1] = buildLabelLayout(strings.last().label, 1)
            strings.last().layouts[2] = buildLabelLayout(strings.last().label, 2)

            labelWidth = max(labelWidth, strings.last().layouts[0]?.width ?: 0)
            labelHeight = max(labelHeight, strings.last().layouts[0]?.height ?: 0)
        }
        this.labelWidth = labelWidth.toFloat() + 2 * labelBackgroundPadding
        this.labelHeight = labelHeight.toFloat() + 2 * labelBackgroundPadding

        if (highlightedStringIndex !in toneIndices.indices)
            highlightedStringIndex = -1
        numHighlightedWithToneIndex = strings.count { it.toneIndex == toneIndexForHighlighting }
        stringIndexForCenteringToneIndex = getClosestStringWithToneIndex(stringIndexForCenteringToneIndex, toneIndexForHighlighting)

        if (isLaidOut) {
            updateStringPositionVariables(width, height)
        }

        requestLayout()
        invalidate()
//        post {
//            yOffset = 0f
//            updateStringPositionVariables(width, height)
//            setAutomaticControl(0L)
////            Log.v("Tuner", "StringView.setStrings (post): yOffset = $yOffset")
//        }
    }

    fun highlightSingleString(stringIndex: Int, animationDuration: Long = 200L) {
//        Log.v("Tuner", "StringView.highlightSingleString: stringIndex=$stringIndex, oldStringIndex=$highlightedStringIndex, highlightBy=$highlightBy")
        if (stringIndex == highlightedStringIndex && highlightBy == HighlightBy.StringIndex)
            return
//        Log.v("Tuner", "StringView.highlightSingleString: stringIndex=$stringIndex, duration=$animationDuration")
        highlightedStringIndex = stringIndex
        highlightBy = HighlightBy.StringIndex
        if (automaticScrollToSelected)
            scrollToStringIndex(highlightedStringIndex, animationDuration)
        else
            invalidate()
    }

    fun highlightByToneIndex(toneIndex: Int, animationDuration: Long = 200L) {
//        Log.v("Tuner", "StringView.highlightByToneIndex: stringIndex=$toneIndex, oldTonIndex=$toneIndexForHighlighting, highlightBy=$highlightBy")
        if (toneIndex == toneIndexForHighlighting && highlightBy == HighlightBy.ToneIndex)
            return

        highlightBy = HighlightBy.ToneIndex
        toneIndexForHighlighting = toneIndex
        numHighlightedWithToneIndex = strings.count { it.toneIndex == toneIndex }

        val closestStringIndexToCenter = (stringStartIndex + stringEndIndex) / 2
        stringIndexForCenteringToneIndex = getClosestStringWithToneIndex(closestStringIndexToCenter, toneIndexForHighlighting)
        if (automaticScrollToSelected)
            scrollToStringIndex(stringIndexForCenteringToneIndex, animationDuration)
        else
            invalidate()
    }

    fun scrollToStringIndex(stringIndex: Int, animationDuration: Long) {
        //val stringIndex = strings.indexOfFirst { it.toneIndex == toneIndex }
        if (stringIndex in strings.indices) {
            flingAnimation.cancel()

            val yOffsetTarget = getYOffsetFromStringIndex(stringIndex)

//            Log.v("Tuner", "StringView.scrollToString yOffsetMin = ${computeOffsetMin()}, yOffsetMax=${computeOffsetMax()}, yOffsetTarget=$yOffsetTarget")
            offsetAnimator.cancel()

            if (animationDuration == 0L) {
                yOffset = yOffsetTarget
                updateStringPositionVariables(width, height)
//                Log.v("Tuner", "StringView.scrollToString (animDur=0): yOffset = $yOffset")
                invalidate()
            } else {
//                Log.v("Tuner", "StringView.scrollToString (animDur>0): yOffset = $yOffset")
                offsetAnimator.duration = animationDuration
                offsetAnimator.setFloatValues(yOffset, yOffsetTarget)
                offsetAnimator.start()
            }
        }
    }

    fun setManualControl() {
//        Log.v("Tuner", "StringView.setManualControl")
        // no manual control if no scrolling is possible
        if (computeOffsetMax() == computeOffsetMin())
            return
        automaticScrollToSelected = false
        //framePaint.color = frameColorOnTouch
        invalidate()
    }

    fun setAutomaticControl(animationDuration: Long = 200L) {
//        Log.v("Tuner", "StringView.setAutomaticControl")
        automaticScrollToSelected = true
        framePaint.color = frameColor
        when (highlightBy) {
            HighlightBy.StringIndex -> scrollToStringIndex(highlightedStringIndex, animationDuration)
            HighlightBy.ToneIndex -> scrollToStringIndex(stringIndexForCenteringToneIndex, animationDuration)
            else -> {}
        }
        invalidate()
    }

    private fun scrollDistance(distance: Float) {
        yOffset -= distance
//            Log.v("Tuner", "StringView.scrollDistance: distance: offsetY=$yOffset, distance=$distance, min=${computeOffsetMin()}, max=${computeOffsetMax()}")
        yOffset = min(yOffset, computeOffsetMax())
        yOffset = max(yOffset, computeOffsetMin())
        updateStringPositionVariables(width, height)
        ViewCompat.postInvalidateOnAnimation(this@StringView)
    }

    private fun drawString(
        xPos: Float,
        yPos: Float,
        stringInfo: StringInfo,
        styleIndex: Int,
        canvas: Canvas
    ) {
        canvas.drawLine(
            paddingLeft.toFloat() + framePaint.strokeWidth, yPos,
            width.toFloat() - paddingRight - framePaint.strokeWidth, yPos, stringPaint[styleIndex]
        )
        canvas.drawRect(
            xPos - 0.5f * labelWidthExpanded,
            yPos - 0.5f * labelHeight,
            xPos + 0.5f * labelWidthExpanded,
            yPos + 0.5f * labelHeight,
            labelBackgroundPaint[styleIndex]
        )

        stringInfo.layouts[styleIndex]?.let { layout ->
            canvas.withTranslation(
                xPos - 0.5f * layout.width, yPos - 0.5f * layout.height
            ) {
                layout.draw(this)
            }
        }
    }

    private fun getNextCenteringStringIndexWithToneIndex(stringIndex: Int, toneIndex: Int): Int {
        var newCenteringStringIndex = -1
        for (i in stringIndex + 1 until strings.size) {
            if (strings[i].toneIndex == toneIndex) {
                newCenteringStringIndex = i
                break
            }
        }

        if (newCenteringStringIndex == -1) {
            for (i in 0 until min(stringIndex + 1, strings.size)) {
                if (strings[i].toneIndex == toneIndex) {
                    newCenteringStringIndex = i
                    break
                }
            }
        }
        return newCenteringStringIndex
    }

    private fun getClosestStringWithToneIndex(stringIndexRef: Int, toneIndex: Int): Int {
        var dist = Int.MAX_VALUE
        var index = 0
        // search positions greater than the reference index
        for (i in max(0, stringIndexRef) until strings.size) {
            if (strings[i].toneIndex == toneIndex) {
                dist = i - stringIndexRef
                index = i
                break
            }
        }
        // search positions smaller than the reference index
        for (i in min(strings.size-1, stringIndexRef-1) downTo 0) {
            if (strings[i].toneIndex == toneIndex) {
                if (stringIndexRef - i < dist)
                    index = i
                break
            }
        }
        return index
    }

    private fun buildLabelLayout(label: CharSequence?, index: Int): StaticLayout? {
        return if (label != null) {
            val desiredWidth = ceil(StaticLayout.getDesiredWidth(label, labelPaint[index])).toInt()
            val builder =
                StaticLayout.Builder.obtain(label, 0, label.length, labelPaint[index], desiredWidth)
            builder.build()
        } else {
            null
        }
    }

    private fun getStringDrawingPositionX(stringIndex: Int): Float {
        val col = stringIndex % numCols
        return paddingLeft + framePaint.strokeWidth + 0.5f * labelSpacing + (0.5f + col) * colWidth
    }

    private fun getStringDrawingPositionY(stringIndex: Int): Float {
        val effectiveHeight =
            height - paddingTop - paddingBottom - 2 * (framePaint.strokeWidth + labelSpacing)
        val stringTotalHeight = getTotalStringHeight()
        val yOffsetMod = if (effectiveHeight > stringTotalHeight) {
            0.5f * (effectiveHeight - stringTotalHeight)
        } else {
            yOffset
        }
        return yOffsetMod + paddingTop + framePaint.strokeWidth + labelSpacing + 0.5f * labelHeight + stringIndex * rowHeight
    }

    private fun getStringIndexAtYCenterAsFloat(): Float {
        val effectiveHeight =
            height - paddingTop - paddingBottom - 2 * (framePaint.strokeWidth + labelSpacing)
        val stringTotalHeight = getTotalStringHeight()
        val yOffsetMod = if (effectiveHeight > stringTotalHeight) {
            0.5f * (effectiveHeight - stringTotalHeight)
        } else {
            yOffset
        }
        // From getStringDrawingPositionY:
        //      yPosition = yOffsetMod + paddingTop + framePaint.strokeWidth + labelSpacing + 0.5f * labelHeight + stringIndex * rowHeight
        // <=> stringIndex = (yPosition - yOffsetMod - paddingTop - framePaint.strokeWidth - labelSpacing - 0.5f * labelHeight) / rowHeight
        val yPosition = 0.5f * (paddingTop + height - paddingBottom)
        return (yPosition - yOffsetMod - paddingTop - framePaint.strokeWidth - labelSpacing - 0.5f * labelHeight) / rowHeight
    }

    private fun updateStringPositionVariables(w: Int, h: Int) {
        val effectiveWidth = w - paddingLeft - paddingRight - 2 * framePaint.strokeWidth
        numCols = (floor((effectiveWidth - labelSpacing) / (labelWidth + labelSpacing))).toInt()
        numCols = max(1, min(numCols, strings.size))
        labelWidthExpanded = (effectiveWidth - labelSpacing) / numCols - labelSpacing
        colWidth = (effectiveWidth - labelSpacing) / numCols
        rowHeight = getRowHeight()

        // visible if
        // yOffset + paddingTop + framePaint.strokeWidth + labelSpacing + 0.5f * labelHeight + i * rowHeight + 0.5f * labelHeight > paddingTop + framePaint.strokeWidth
        // i * rowHeight > -yOffset - labelHeight - labelSpacing
        // i > - (yOffset + labelHeight + labelSpacing) / rowHeight
        //
        // yOffset + paddingTop + framePaint.strokeWith + labelSpacing + 0.5f * labelHeight + i * rowHeight - 0.5f * labelHeight < height - paddingBottom - framePaint.strokeWidth
        // i * rowHeight < height - paddingBottom - paddingTop - yOffset - 2 * framePaint.strokeWidth - labelSpacing
        // i > (height - paddingBottom - paddingTop - yOffset - 2 * framePaint.strokeWidth - labelSpacing) / rowHeight
        stringStartIndex =
            max(0, floor(-(yOffset + labelHeight + labelSpacing) / rowHeight).toInt())
        stringEndIndex = min(
            strings.size - 1,
            ceil((h - paddingBottom - paddingTop - yOffset - 2 * framePaint.strokeWidth - labelSpacing) / rowHeight).toInt()
        )
    }

    private fun computeOffsetMin(): Float {
        val effectiveHeight = height - paddingTop - paddingBottom - 2 * framePaint.strokeWidth
        val stringTotalHeight = getTotalStringHeight()
//        Log.v("Tuner", "StringView.computeOffsetMax: stringTotalHeight=$stringTotalHeight, effectiveHeight=$effectiveHeight")
        return min(0f, effectiveHeight - stringTotalHeight - 2 * labelSpacing)
    }

    private fun computeOffsetMax(): Float {
        return 0f
    }

    private fun getRowHeight(): Float {
        val maxStrokeWidth = stringPaint.maxOf { it.strokeWidth }
        return 0.5f * (labelHeight + maxStrokeWidth) + labelSpacing
    }

    private fun getTotalStringHeight(): Float {
        return (strings.size - 1) * getRowHeight() + labelHeight
    }

    private fun getYOffsetFromStringIndex(stringIndex: Int): Float {
        return getYOffsetFromStringIndex(stringIndex.toFloat())
    }

    private fun getYOffsetFromStringIndex(stringIndex: Float): Float {
        val yPosOfString = 0.5f * (paddingTop + height - paddingBottom)
        // from getStringDrawingPositionY
        // yPosOfString = yOffsetMod + paddingTop + framePaint.strokeWidth + labelSpacing + 0.5f * labelHeight + stringIndex * rowHeight
        // -> yOffset = yPosOfString - (paddingTop + framePaint.strokeWidth + labelSpacing + 0.5f * labelHeight + stringIndex * rowHeight)
        var offset = yPosOfString - (paddingTop + framePaint.strokeWidth + labelSpacing + 0.5f * labelHeight + stringIndex * rowHeight)
        offset = min(offset, computeOffsetMax())
        offset = max(offset, computeOffsetMin())
        return offset
    }

    companion object {
        const val NO_ACTIVE_TONE_INDEX = Int.MAX_VALUE
        const val NO_ANCHOR = Float.MAX_VALUE
    }
}
