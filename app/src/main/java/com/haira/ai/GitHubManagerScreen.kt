package com.haira.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GithubPanel = Color(0xFF0D1826)
private val GithubCyan = Color(0xFF55E6F2)
private val GithubMuted = Color(0xFF8CA4B8)
private val GithubGold = Color(0xFFD7B56D)

@Composable
fun GitHubManagerScreen() {
 val context=androidx.compose.ui.platform.LocalContext.current
 val client=remember{GitHubManagerClient()}
 val tokenStore=remember{GitHubTokenStore(context)}
 var token by remember{mutableStateOf("")}
 var connectedUser by remember{mutableStateOf("")}
 var status by remember{mutableStateOf("Connect your GitHub account to begin.")}
 var busy by remember{mutableStateOf(false)}
 var repos by remember{mutableStateOf(emptyList<GitHubRepoItem>())}
 var selectedRepo by remember{mutableStateOf<GitHubRepoItem?>(null)}
 var currentPath by remember{mutableStateOf("")}
 var entries by remember{mutableStateOf(emptyList<GitHubEntry>())}
 var filePath by remember{mutableStateOf("")}
 var fileSha by remember{mutableStateOf("")}
 var fileText by remember{mutableStateOf("")}
 var workflows by remember{mutableStateOf(emptyList<GitHubWorkflow>())}
 var runs by remember{mutableStateOf(emptyList<GitHubRun>())}
 var showActions by remember{mutableStateOf(false)}
 var commitMessage by remember{mutableStateOf("Update file from HAIRA AI")}
 val savedToken=remember{tokenStore.read()}
 DisposableEffect(Unit){onDispose{client.close()}}

 fun loadRepos(auth:String){
  busy=true
  client.repositories(auth){result->
   busy=false
   result.onSuccess{repos=it;status="Loaded ${it.size} repositories."}
    .onFailure{status=it.message?: "Could not load repositories."}
  }
 }
 fun loadPath(repo:GitHubRepoItem,path:String){
  val parts=repo.fullName.split("/")
  if(parts.size!=2)return
  busy=true
  client.contents(tokenStore.read(),parts[0],parts[1],path){result->
   busy=false
   result.onSuccess{entries=it;currentPath=path;filePath="";status="Opened ${if(path.isBlank()) "repository root" else path}."}
    .onFailure{status=it.message?: "Could not read repository contents."}
  }
 }
 fun loadActions(repo:GitHubRepoItem){
  val parts=repo.fullName.split("/")
  if(parts.size!=2)return
  busy=true
  client.workflows(tokenStore.read(),parts[0],parts[1]){result->
   busy=false
   result.onSuccess{workflows=it;showActions=true;status="Loaded ${it.size} workflows."}
    .onFailure{status=it.message?: "Could not load workflows. Check Actions permissions."}
  }
  client.runs(tokenStore.read(),parts[0],parts[1]){result->result.onSuccess{runs=it}}
 }

 Column(Modifier.fillMaxSize().padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Text("GITHUB MANAGER",color=GithubCyan,fontSize=11.sp,letterSpacing=2.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
  Text("Connect repositories, inspect files and manage GitHub Actions.",color=GithubMuted,fontSize=11.sp)
  if(connectedUser.isBlank()){
   Surface(color=GithubPanel,shape=RoundedCornerShape(15.dp),modifier=Modifier.fillMaxWidth()){
    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
     Text("CONNECT GITHUB",color=GithubCyan,fontSize=11.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
     OutlinedTextField(value=token,onValueChange={token=it},modifier=Modifier.fillMaxWidth(),label={Text("Fine-grained personal access token")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
     Text("Create a fine-grained token on GitHub with access only to the repositories you choose. Required: Metadata read, Contents read/write, Actions read/write. Never share this token in chat. HAIRA stores it encrypted with Android Keystore.",color=GithubMuted,fontSize=10.sp,lineHeight=15.sp)
     Button(onClick={
      if(!busy&&token.isNotBlank()){
       busy=true
       client.verify(token.trim()){result->
        busy=false
        result.onSuccess{login->connectedUser=login;tokenStore.save(token.trim());token="";status="Connected as @${login}";loadRepos(tokenStore.read())}
         .onFailure{status=it.message?: "GitHub connection failed."}
       }
      }
     },enabled=!busy&&token.isNotBlank(),modifier=Modifier.fillMaxWidth()){
      Text(if(busy)"CONNECTING..." else "CONNECT GITHUB")
     }
     if(savedToken.isNotBlank()) OutlinedButton(onClick={token=savedToken},modifier=Modifier.fillMaxWidth()){Text("USE SAVED CONNECTION")}
    }
   }
  }else{
   Surface(color=GithubPanel,shape=RoundedCornerShape(13.dp),modifier=Modifier.fillMaxWidth()){
    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){
      Column(Modifier.weight(1f)){Text("CONNECTED",color=GithubCyan,fontSize=9.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold);Text("@${connectedUser}",color=Color.White,fontSize=14.sp)}
      TextButton(onClick={tokenStore.save("");connectedUser="";repos=emptyList();selectedRepo=null;status="Disconnected."}){Text("DISCONNECT")}
     }
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      OutlinedButton(onClick={loadRepos(tokenStore.read())},enabled=!busy){Text("REFRESH REPOS",fontSize=10.sp)}
      OutlinedButton(onClick={selectedRepo?.let{loadActions(it)}},enabled=selectedRepo!=null&&!busy){Text("ACTIONS",fontSize=10.sp)}
     }
    }
   }
   if(selectedRepo==null){
    Text("YOUR REPOSITORIES",color=GithubMuted,fontSize=10.sp)
    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)){
     items(repos){repo->
      Surface(color=GithubPanel,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().clickable{
       selectedRepo=repo;showActions=false;loadPath(repo,"")
      }){
       Column(Modifier.padding(12.dp)){
        Text(repo.fullName,color=Color.White,fontSize=12.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
        Text("${if(repo.privateRepo)"Private" else "Public"} • default: ${repo.defaultBranch}",color=GithubCyan,fontSize=9.sp)
        if(repo.description.isNotBlank())Text(repo.description,color=GithubMuted,fontSize=10.sp,maxLines=2)
       }
      }
     }
    }
   }else{
    Row(verticalAlignment=Alignment.CenterVertically){
     TextButton(onClick={selectedRepo=null;entries=emptyList();filePath="";showActions=false}){Text("← REPOSITORIES")}
     Column(Modifier.weight(1f)){Text(selectedRepo!!.fullName,color=Color.White,fontSize=11.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold);Text(currentPath.ifBlank{"Repository root"},color=GithubMuted,fontSize=9.sp)}
    }
    if(showActions){
     Text("WORKFLOWS",color=GithubCyan,fontSize=10.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
     LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
      items(workflows){flow->
       Surface(color=GithubPanel,shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(10.dp)){
         Text(flow.name,color=Color.White,fontSize=11.sp)
         Text("${flow.state} • ${flow.path}",color=GithubMuted,fontSize=9.sp)
         TextButton(onClick={
          val repo=selectedRepo!!
          val p=repo.fullName.split("/")
          busy=true
          client.dispatch(tokenStore.read(),p[0],p[1],flow.id,repo.defaultBranch){result->
           busy=false;status=result.getOrElse{it.message?: "Workflow dispatch failed."}
           if(result.isSuccess)loadActions(repo)
          }
         },enabled=!busy&&flow.state=="active"){Text("RUN WORKFLOW",fontSize=9.sp)}
        }
       }
      }
     }
     Text("RECENT RUNS",color=GithubCyan,fontSize=10.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
     runs.take(5).forEach{run->Text("${run.name} • ${run.status} ${run.conclusion}",color=GithubMuted,fontSize=9.sp)}
    }else if(filePath.isNotBlank()){
     Text("EDIT FILE: $filePath",color=GithubCyan,fontSize=10.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
     OutlinedTextField(value=fileText,onValueChange={fileText=it},modifier=Modifier.weight(1f).fillMaxWidth(),textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=11.sp),minLines=8)
     OutlinedTextField(value=commitMessage,onValueChange={commitMessage=it},modifier=Modifier.fillMaxWidth(),label={Text("Commit message")},singleLine=true)
     Button(onClick={
      val repo=selectedRepo!!;val p=repo.fullName.split("/")
      busy=true
      client.saveFile(tokenStore.read(),p[0],p[1],filePath,fileText,fileSha,repo.defaultBranch,commitMessage.trim().ifBlank{"Update $filePath"}){result->
       busy=false;status=result.getOrElse{it.message?: "Save failed."}
       if(result.isSuccess){filePath="";loadPath(repo,currentPath)}
      }
     },enabled=!busy&&fileSha.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(if(busy)"SAVING..." else "COMMIT FILE")}
     OutlinedButton(onClick={filePath="";loadPath(selectedRepo!!,currentPath)},modifier=Modifier.fillMaxWidth()){Text("CANCEL EDIT")}
    }else{
     Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
      if(currentPath.isNotBlank())OutlinedButton(onClick={loadPath(selectedRepo!!,currentPath.substringBeforeLast("/", ""))},enabled=!busy){Text("UP")}
      OutlinedButton(onClick={loadPath(selectedRepo!!,currentPath)},enabled=!busy){Text("REFRESH")}
      OutlinedButton(onClick={loadActions(selectedRepo!!)},enabled=!busy){Text("ACTIONS")}
     }
     LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
      items(entries){entry->
       Surface(color=GithubPanel,shape=RoundedCornerShape(9.dp),modifier=Modifier.fillMaxWidth().clickable{
        if(entry.type=="dir")loadPath(selectedRepo!!,entry.path) else {
         val p=selectedRepo!!.fullName.split("/")
         busy=true
         client.readFile(tokenStore.read(),p[0],p[1],entry.path){result->
          busy=false
          result.onSuccess{pair->filePath=entry.path;fileText=pair.first;fileSha=pair.second;status="File loaded. Review changes before committing."}
           .onFailure{status=it.message?: "Could not read file."}
         }
        }
       }){
        Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
         Text(if(entry.type=="dir")"▰" else "▤",color=if(entry.type=="dir")GithubGold else GithubCyan)
         Spacer(Modifier.width(9.dp));Text(entry.name,color=Color.White,fontSize=11.sp)
        }
       }
      }
     }
    }
   }
  }
  Text(status,color=if(status.contains("failed",true)||status.contains("error",true))Color(0xFFFFC477) else GithubMuted,fontSize=10.sp)
 }
}
