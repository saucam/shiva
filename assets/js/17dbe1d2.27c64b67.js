"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[764],{3905:(e,t,n)=>{n.d(t,{Zo:()=>p,kt:()=>h});var a=n(7294);function r(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function i(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);t&&(a=a.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,a)}return n}function s(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?i(Object(n),!0).forEach((function(t){r(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):i(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function o(e,t){if(null==e)return{};var n,a,r=function(e,t){if(null==e)return{};var n,a,r={},i=Object.keys(e);for(a=0;a<i.length;a++)n=i[a],t.indexOf(n)>=0||(r[n]=e[n]);return r}(e,t);if(Object.getOwnPropertySymbols){var i=Object.getOwnPropertySymbols(e);for(a=0;a<i.length;a++)n=i[a],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(r[n]=e[n])}return r}var l=a.createContext({}),c=function(e){var t=a.useContext(l),n=t;return e&&(n="function"==typeof e?e(t):s(s({},t),e)),n},p=function(e){var t=c(e.components);return a.createElement(l.Provider,{value:t},e.children)},u="mdxType",m={inlineCode:"code",wrapper:function(e){var t=e.children;return a.createElement(a.Fragment,{},t)}},d=a.forwardRef((function(e,t){var n=e.components,r=e.mdxType,i=e.originalType,l=e.parentName,p=o(e,["components","mdxType","originalType","parentName"]),u=c(n),d=r,h=u["".concat(l,".").concat(d)]||u[d]||m[d]||i;return n?a.createElement(h,s(s({ref:t},p),{},{components:n})):a.createElement(h,s({ref:t},p))}));function h(e,t){var n=arguments,r=t&&t.mdxType;if("string"==typeof e||r){var i=n.length,s=new Array(i);s[0]=d;var o={};for(var l in t)hasOwnProperty.call(t,l)&&(o[l]=t[l]);o.originalType=e,o[u]="string"==typeof e?e:r,s[1]=o;for(var c=2;c<i;c++)s[c]=n[c];return a.createElement.apply(null,s)}return a.createElement.apply(null,n)}d.displayName="MDXCreateElement"},7465:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>l,contentTitle:()=>s,default:()=>m,frontMatter:()=>i,metadata:()=>o,toc:()=>c});var a=n(7462),r=(n(7294),n(3905));const i={},s="shiva [WIP]",o={unversionedId:"README",id:"README",title:"shiva [WIP]",description:"shiva is a library for Simple High dimensional Indexed Vector search Algorithms.",source:"@site/../shiva-docs/target/mdoc/README.md",sourceDirName:".",slug:"/",permalink:"/shiva/docs/",draft:!1,tags:[],version:"current",frontMatter:{},sidebar:"tutorialSidebar"},l={},c=[{value:"Overview",id:"overview",level:2},{value:"Installation",id:"installation",level:2},{value:"Usage",id:"usage",level:2},{value:"Contributing",id:"contributing",level:2}],p={toc:c},u="wrapper";function m(e){let{components:t,...n}=e;return(0,r.kt)(u,(0,a.Z)({},p,n,{components:t,mdxType:"MDXLayout"}),(0,r.kt)("h1",{id:"shiva-wip"},"shiva ","[WIP]"),(0,r.kt)("p",null,"shiva is a library for Simple High dimensional Indexed Vector search Algorithms."),(0,r.kt)("p",null,(0,r.kt)("img",{parentName:"p",src:"https://img.shields.io/github/license/saucam/shiva",alt:null}),"\n",(0,r.kt)("img",{parentName:"p",src:"https://img.shields.io/github/issues/saucam/shiva",alt:null}),"\n",(0,r.kt)("a",{parentName:"p",href:"https://github.com/saucam/shiva/actions/workflows/ci.yaml"},(0,r.kt)("img",{parentName:"a",src:"https://github.com/saucam/shiva/actions/workflows/ci.yaml/badge.svg?branch=main",alt:"CI"})),"\n",(0,r.kt)("a",{parentName:"p",href:"https://codecov.io/gh/saucam/shiva"},(0,r.kt)("img",{parentName:"a",src:"https://codecov.io/gh/saucam/shiva/branch/main/graph/badge.svg?token=7UDJE3NX5K",alt:"codecov"})),"\n",(0,r.kt)("a",{parentName:"p",href:"https://s01.oss.sonatype.org/content/repositories/snapshots/io/github/saucam/shiva-core_2.13/"},(0,r.kt)("img",{parentName:"a",src:"https://img.shields.io/nexus/s/io.github.saucam/shiva-core_2.13?server=https%3A%2F%2Fs01.oss.sonatype.org%2F",alt:"Sonatype Snapshots"})),"\n",(0,r.kt)("a",{parentName:"p",href:"https://s01.oss.sonatype.org/content/repositories/releases/io/github/saucam/shiva-core_2.13/"},(0,r.kt)("img",{parentName:"a",src:"https://img.shields.io/nexus/r/io.github.saucam/shiva-core_2.13?nexusVersion=2&server=https%3A%2F%2Fs01.oss.sonatype.org",alt:"Sonatype Releases"}))),(0,r.kt)("h2",{id:"overview"},"Overview"),(0,r.kt)("p",null,"Basic guiding principle is to be:"),(0,r.kt)("ul",null,(0,r.kt)("li",{parentName:"ul"},"Simple (non-distributed, single threaded indexing, easy to use)"),(0,r.kt)("li",{parentName:"ul"},"Support high dimensional vectors, optimize memory for speed"),(0,r.kt)("li",{parentName:"ul"},"Support many distance metrics"),(0,r.kt)("li",{parentName:"ul"},"Scale out to different indices and algorithms")),(0,r.kt)("h2",{id:"installation"},"Installation"),(0,r.kt)("p",null,"To use Shiva, add the following to your ",(0,r.kt)("inlineCode",{parentName:"p"},"build.sbt")),(0,r.kt)("p",null,"For releases versions:"),(0,r.kt)("pre",null,(0,r.kt)("code",{parentName:"pre",className:"language-scala"},'resolvers +=\n  "Sonatype OSS Releases" at "https://s01.oss.sonatype.org/content/repositories/releases"\n\nlibraryDependencies ++= Seq(\n  "io.github.saucam" %% "shiva-core" % "<version>"\n)\n')),(0,r.kt)("p",null,"For snapshot versions:"),(0,r.kt)("pre",null,(0,r.kt)("code",{parentName:"pre",className:"language-scala"},'resolvers +=\n  "Sonatype OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"\n\nlibraryDependencies ++= Seq(\n  "io.github.saucam" %% "shiva-core" % "<version>"\n)\n')),(0,r.kt)("h2",{id:"usage"},"Usage"),(0,r.kt)("p",null,"The following gives a simple example on how to use the hnsw index in the library after adding the dependency:"),(0,r.kt)("pre",null,(0,r.kt)("code",{parentName:"pre",className:"language-scala"},"val index = HnswIndexBuilder[Int, Double, IntDoubleIndexItem](\n  dimensions = 3,\n  maxItemCount = 1000000,\n  m = 32,\n  distanceCalculator = new EuclideanDistanceDouble\n).build()\n\nval item1 = IntDoubleIndexItem(1, Vector(4.05d, 1.06d, 7.8d))\nval item2 = IntDoubleIndexItem(2, Vector(8.01d, 2.06d, 1.8d))\nval item3 = IntDoubleIndexItem(3, Vector(9.34d, 3.06d, 3.1d))\n\nindex.add(item1)\nindex.add(item2)\nindex.add(item3)\n\nval results = index.findKSimilarItems(item1.id, 10)\nresults.foreach(println())\n")),(0,r.kt)("h2",{id:"contributing"},"Contributing"),(0,r.kt)("p",null,"See the ",(0,r.kt)("a",{parentName:"p",href:"/shiva/docs/CONTRIBUTING"},"contributor's guide")))}m.isMDXComponent=!0}}]);