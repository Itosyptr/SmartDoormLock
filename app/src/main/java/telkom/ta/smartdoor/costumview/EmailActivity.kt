package telkom.ta.smartdoor.costumview

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.ViewParent
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.regex.Pattern

class EmailActivity : TextInputEditText {

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private fun init() {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val parentLayout = findTextInputLayoutParent()
                if (!isValidEmail(s.toString())) {
                    parentLayout?.error = "Invalid Email"
                } else {
                    parentLayout?.error = null
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun isValidEmail(email: String): Boolean {
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        return Pattern.matches(emailPattern, email)
    }

    // Mencari parent TextInputLayout agar bisa menggunakan setError di level yang tepat
    private fun findTextInputLayoutParent(): TextInputLayout? {
        var parent: ViewParent? = parent
        while (parent != null && parent !is TextInputLayout) {
            parent = parent.parent
        }
        return parent as? TextInputLayout
    }
}
