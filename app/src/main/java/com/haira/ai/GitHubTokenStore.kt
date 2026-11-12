package com.haira.ai
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
class GitHubTokenStore(context: Context) {
 private val prefs=context.applicationContext.getSharedPreferences("haira_github_secure",Context.MODE_PRIVATE)
 private val alias="haira_github_token_v1"
 fun save(token:String) {
  if(token.isBlank()){prefs.edit().remove("token").apply();return}
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,getOrCreateKey())
  val encrypted=cipher.doFinal(token.trim().toByteArray(Charsets.UTF_8))
  prefs.edit().putString("token",Base64.encodeToString(cipher.iv+encrypted,Base64.NO_WRAP)).apply()
 }
 fun read():String {
  val packedText=prefs.getString("token",null)?:return ""
  return try {
   val packed=Base64.decode(packedText,Base64.NO_WRAP);require(packed.size>12)
   val cipher=Cipher.getInstance("AES/GCM/NoPadding")
   cipher.init(Cipher.DECRYPT_MODE,getOrCreateKey(),GCMParameterSpec(128,packed.copyOfRange(0,12)))
   String(cipher.doFinal(packed.copyOfRange(12,packed.size)),Charsets.UTF_8)
  } catch(_:Exception){prefs.edit().remove("token").apply();""}
 }
 private fun getOrCreateKey():SecretKey {
  val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  (store.getKey(alias,null) as? SecretKey)?.let{return it}
  val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
  generator.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
   .setRandomizedEncryptionRequired(true).build())
  return generator.generateKey()
 }
}
