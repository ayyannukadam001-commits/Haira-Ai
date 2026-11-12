package com.haira.ai
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class GitHubRepoItem(val fullName:String,val privateRepo:Boolean,val defaultBranch:String,val description:String)
data class GitHubEntry(val name:String,val path:String,val type:String,val sha:String)
data class GitHubWorkflow(val id:Long,val name:String,val state:String,val path:String)
data class GitHubRun(val id:Long,val name:String,val status:String,val conclusion:String,val branch:String,val url:String)

class GitHubManagerClient {
 private val executor=Executors.newSingleThreadExecutor()
 private val main=Handler(Looper.getMainLooper())
 fun verify(token:String,callback:(Result<String>)->Unit)=async(callback){
  JSONObject(request(token,"/user")).optString("login").ifBlank{throw IllegalStateException("GitHub account name missing.")}
 }
 fun repositories(token:String,callback:(Result<List<GitHubRepoItem>>)->Unit)=async(callback){
  val json=JSONArray(request(token,"/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member"))
  buildList { for(i in 0 until json.length()){val x=json.getJSONObject(i);add(GitHubRepoItem(x.optString("full_name"),x.optBoolean("private"),x.optString("default_branch"),x.optString("description")))} }
 }
 fun contents(token:String,owner:String,repo:String,path:String,callback:(Result<List<GitHubEntry>>)->Unit)=async(callback){
  val suffix=if(path.isBlank()) "" else "/"+encodePath(path)
  val array=JSONArray(request(token,"/repos/$owner/$repo/contents$suffix"))
  buildList { for(i in 0 until array.length()){val x=array.getJSONObject(i);add(GitHubEntry(x.optString("name"),x.optString("path"),x.optString("type"),x.optString("sha")))} }
 }
 fun readFile(token:String,owner:String,repo:String,path:String,callback:(Result<Pair<String,String>>)->Unit)=async(callback){
  val x=JSONObject(request(token,"/repos/$owner/$repo/contents/"+encodePath(path)))
  val bytes=Base64.decode(x.optString("content").replace("\\n",""),Base64.DEFAULT)
  String(bytes,Charsets.UTF_8) to x.optString("sha")
 }
 fun saveFile(token:String,owner:String,repo:String,path:String,content:String,sha:String,branch:String,message:String,callback:(Result<String>)->Unit)=async(callback){
  val body=JSONObject().put("message",message).put("content",Base64.encodeToString(content.toByteArray(Charsets.UTF_8),Base64.NO_WRAP)).put("sha",sha).put("branch",branch)
  val result=JSONObject(request(token,"/repos/$owner/$repo/contents/"+encodePath(path),"PUT",body))
  result.optJSONObject("commit")?.optString("sha").orEmpty().ifBlank{"Commit created"}
 }
 fun workflows(token:String,owner:String,repo:String,callback:(Result<List<GitHubWorkflow>>)->Unit)=async(callback){
  val array=JSONObject(request(token,"/repos/$owner/$repo/actions/workflows?per_page=100")).optJSONArray("workflows")?:JSONArray()
  buildList { for(i in 0 until array.length()){val x=array.getJSONObject(i);add(GitHubWorkflow(x.optLong("id"),x.optString("name"),x.optString("state"),x.optString("path")))} }
 }
 fun runs(token:String,owner:String,repo:String,callback:(Result<List<GitHubRun>>)->Unit)=async(callback){
  val array=JSONObject(request(token,"/repos/$owner/$repo/actions/runs?per_page=15")).optJSONArray("workflow_runs")?:JSONArray()
  buildList { for(i in 0 until array.length()){val x=array.getJSONObject(i);add(GitHubRun(x.optLong("id"),x.optString("name"),x.optString("status"),x.optString("conclusion"),x.optString("head_branch"),x.optString("html_url")))} }
 }
 fun dispatch(token:String,owner:String,repo:String,workflowId:Long,branch:String,callback:(Result<String>)->Unit)=async(callback){
  request(token,"/repos/$owner/$repo/actions/workflows/$workflowId/dispatches","POST",JSONObject().put("ref",branch))
  "Workflow dispatch request accepted by GitHub."
 }
 private fun <T> async(callback:(Result<T>)->Unit,block:()->T){
  executor.execute{val result=runCatching(block);main.post{callback(result)}}
 }
 private fun request(token:String,path:String,method:String="GET",body:JSONObject?=null):String {
  require(token.isNotBlank()){"Connect GitHub with a token first."}
  val c=(URL("https://api.github.com$path").openConnection() as HttpURLConnection).apply{
   requestMethod=method;connectTimeout=15000;readTimeout=30000
   setRequestProperty("Accept","application/vnd.github+json")
   setRequestProperty("Authorization","Bearer $token")
   setRequestProperty("X-GitHub-Api-Version","2022-11-28")
   if(body!=null){doOutput=true;setRequestProperty("Content-Type","application/json")}
  }
  try{
   if(body!=null)c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
   val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream
   val response=stream?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}.orEmpty()
   if(code !in 200..299){
    val detail=runCatching{JSONObject(response).optString("message")}.getOrNull().orEmpty()
    val hint=when(code){401->" Token invalid or expired.";403->" Permission missing, rate limit reached, or organization policy blocked this action.";404->" Repository/file/workflow not found, or token lacks access.";409->" File changed on GitHub; refresh before saving.";else->""}
    throw IllegalStateException("GitHub API HTTP $code.$hint $detail".trim())
   }
   return response
  }finally{c.disconnect()}
 }
 private fun encodePath(path:String)=path.split("/").joinToString("/") { Uri.encode(it) }
 fun close(){executor.shutdownNow()}
}
