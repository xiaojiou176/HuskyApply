# Resume Analysis AI Chain for Intelligent Resume Processing and Optimization
# Extends the existing LangChain architecture with multi-stage resume analysis capabilities

import asyncio
import logging
import time
from typing import Dict, List, Optional, Any, Tuple, AsyncGenerator
import json
from dataclasses import dataclass, asdict
from enum import Enum

from langchain_core.language_models import BaseLanguageModel
from langchain_core.output_parsers import JsonOutputParser, StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import RunnablePassthrough

from ai_chain import create_llm, _execute_with_circuit_breaker
from semantic_cache import get_semantic_cache
from ai_optimizer import get_ai_optimizer
from streaming_handler import get_streaming_handler
from exceptions import AIProviderException, JobProcessingException


class AnalysisType(Enum):
    """Types of resume analysis available."""
    STRUCTURE_ANALYSIS = "structure_analysis"
    SKILLS_MATCHING = "skills_matching"
    OPTIMIZATION_SUGGESTIONS = "optimization_suggestions"
    COMPREHENSIVE = "comprehensive"


class ResumeSection(Enum):
    """Resume sections for structured analysis."""
    CONTACT_INFO = "contact_info"
    SUMMARY = "summary"
    EXPERIENCE = "experience"
    SKILLS = "skills"
    EDUCATION = "education"
    PROJECTS = "projects"
    CERTIFICATIONS = "certifications"
    OTHER = "other"


@dataclass
class ResumeStructureAnalysis:
    """Structured representation of resume analysis results."""
    sections_present: List[str]
    sections_missing: List[str]
    format_score: float  # 0.0 to 1.0
    organization_score: float  # 0.0 to 1.0
    completeness_score: float  # 0.0 to 1.0
    overall_structure_score: float  # 0.0 to 1.0
    recommendations: List[str]
    word_count: int
    estimated_reading_time_minutes: float


@dataclass
class SkillsMatchingAnalysis:
    """Skills matching analysis results."""
    extracted_skills: List[str]
    matched_skills: List[str]
    missing_skills: List[str]
    skill_categories: Dict[str, List[str]]  # technical, soft, domain-specific
    match_percentage: float  # 0.0 to 100.0
    skill_level_assessment: Dict[str, str]  # skill -> beginner/intermediate/advanced
    recommendations: List[str]
    priority_skills_to_add: List[str]


@dataclass
class OptimizationSuggestions:
    """Resume optimization suggestions."""
    content_improvements: List[Dict[str, str]]  # section -> suggestion
    formatting_suggestions: List[str]
    keyword_optimization: List[str]
    ats_compatibility_score: float  # 0.0 to 1.0
    action_items: List[Dict[str, Any]]  # priority, description, impact
    estimated_improvement_impact: float  # 0.0 to 1.0


@dataclass
class ComprehensiveResumeAnalysis:
    """Complete resume analysis results."""
    structure_analysis: ResumeStructureAnalysis
    skills_matching: SkillsMatchingAnalysis
    optimization_suggestions: OptimizationSuggestions
    overall_score: float  # 0.0 to 1.0
    key_strengths: List[str]
    critical_issues: List[str]
    estimated_processing_cost: float
    analysis_metadata: Dict[str, Any]


class ResumeAnalysisChain:
    """Enhanced resume analysis chain with multi-stage AI processing."""
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
        self.cache = get_semantic_cache()
        self.optimizer = get_ai_optimizer()
        self.streaming_handler = get_streaming_handler()
    
    async def analyze_resume_comprehensive(
        self,
        resume_text: str,
        job_description: Optional[str] = None,
        analysis_type: AnalysisType = AnalysisType.COMPREHENSIVE,
        model_provider: str = "openai",
        model_name: Optional[str] = None,
        user_id: Optional[str] = None,
        job_id: Optional[str] = None,
        enable_streaming: bool = True,
        enable_caching: bool = True
    ) -> ComprehensiveResumeAnalysis:
        """
        Comprehensive resume analysis with intelligent caching and streaming.
        
        Args:
            resume_text: Raw resume text content
            job_description: Optional job description for skills matching
            analysis_type: Type of analysis to perform
            model_provider: AI provider ("openai" or "anthropic")
            model_name: Specific model name
            user_id: User identifier for analytics
            job_id: Job identifier for tracking
            enable_streaming: Enable real-time streaming updates
            enable_caching: Enable semantic caching
            
        Returns:
            ComprehensiveResumeAnalysis with detailed insights
        """
        start_time = time.time()
        self.logger.info(f"Starting comprehensive resume analysis for job {job_id}")
        
        try:
            # Check cache first
            if enable_caching and self.cache:
                cache_key = f"resume_analysis_{hash(resume_text)}_{hash(job_description or '')}"
                cached_result = await self._get_cached_analysis(cache_key)
                if cached_result:
                    self.logger.info(f"Cache hit for resume analysis: {job_id}")
                    return cached_result
            
            # Initialize AI models
            llm = create_llm(model_provider, model_name, temperature=0.3)  # Lower temp for analysis
            
            # Run analysis stages in parallel for efficiency
            structure_task = self._analyze_structure(resume_text, llm, job_id)
            
            if job_description:
                skills_task = self._analyze_skills_matching(resume_text, job_description, llm, job_id)
                optimization_task = self._generate_optimization_suggestions(
                    resume_text, job_description, llm, job_id
                )
            else:
                # Basic skills analysis without job matching
                skills_task = self._analyze_skills_basic(resume_text, llm, job_id)
                optimization_task = self._generate_general_optimizations(resume_text, llm, job_id)
            
            # Execute analysis stages
            if enable_streaming:
                return await self._execute_streaming_analysis(
                    structure_task, skills_task, optimization_task, 
                    resume_text, job_id, start_time
                )
            else:
                return await self._execute_batch_analysis(
                    structure_task, skills_task, optimization_task,
                    resume_text, job_id, start_time, cache_key if enable_caching else None
                )
                
        except Exception as e:
            self.logger.error(f"Resume analysis failed for job {job_id}: {e}")
            raise JobProcessingException(f"Resume analysis failed: {str(e)}")
    
    async def _analyze_structure(
        self, resume_text: str, llm: BaseLanguageModel, job_id: str
    ) -> ResumeStructureAnalysis:
        """Analyze resume structure and organization."""
        prompt = self._get_structure_analysis_prompt()
        template = ChatPromptTemplate.from_template(prompt)
        chain = template | llm | JsonOutputParser()
        
        try:
            result = await _execute_with_circuit_breaker(
                chain, {"resume_text": resume_text}, llm.__class__.__name__, self.optimizer
            )
            
            return ResumeStructureAnalysis(
                sections_present=result.get("sections_present", []),
                sections_missing=result.get("sections_missing", []),
                format_score=result.get("format_score", 0.0),
                organization_score=result.get("organization_score", 0.0),
                completeness_score=result.get("completeness_score", 0.0),
                overall_structure_score=result.get("overall_structure_score", 0.0),
                recommendations=result.get("recommendations", []),
                word_count=len(resume_text.split()),
                estimated_reading_time_minutes=len(resume_text.split()) / 200  # Average reading speed
            )
        except Exception as e:
            self.logger.error(f"Structure analysis failed for job {job_id}: {e}")
            return self._create_fallback_structure_analysis(resume_text)
    
    async def _analyze_skills_matching(
        self, resume_text: str, job_description: str, llm: BaseLanguageModel, job_id: str
    ) -> SkillsMatchingAnalysis:
        """Analyze skills matching between resume and job description."""
        prompt = self._get_skills_matching_prompt()
        template = ChatPromptTemplate.from_template(prompt)
        chain = template | llm | JsonOutputParser()
        
        try:
            result = await _execute_with_circuit_breaker(
                chain, 
                {"resume_text": resume_text, "job_description": job_description},
                llm.__class__.__name__, 
                self.optimizer
            )
            
            return SkillsMatchingAnalysis(
                extracted_skills=result.get("extracted_skills", []),
                matched_skills=result.get("matched_skills", []),
                missing_skills=result.get("missing_skills", []),
                skill_categories=result.get("skill_categories", {}),
                match_percentage=result.get("match_percentage", 0.0),
                skill_level_assessment=result.get("skill_level_assessment", {}),
                recommendations=result.get("recommendations", []),
                priority_skills_to_add=result.get("priority_skills_to_add", [])
            )
        except Exception as e:
            self.logger.error(f"Skills matching analysis failed for job {job_id}: {e}")
            return self._create_fallback_skills_analysis(resume_text, job_description)
    
    async def _analyze_skills_basic(
        self, resume_text: str, llm: BaseLanguageModel, job_id: str
    ) -> SkillsMatchingAnalysis:
        """Basic skills analysis without job description matching."""
        prompt = self._get_basic_skills_prompt()
        template = ChatPromptTemplate.from_template(prompt)
        chain = template | llm | JsonOutputParser()
        
        try:
            result = await _execute_with_circuit_breaker(
                chain, {"resume_text": resume_text}, llm.__class__.__name__, self.optimizer
            )
            
            return SkillsMatchingAnalysis(
                extracted_skills=result.get("extracted_skills", []),
                matched_skills=[],  # No job to match against
                missing_skills=[],
                skill_categories=result.get("skill_categories", {}),
                match_percentage=0.0,  # No matching without job description
                skill_level_assessment=result.get("skill_level_assessment", {}),
                recommendations=result.get("recommendations", []),
                priority_skills_to_add=[]
            )
        except Exception as e:
            self.logger.error(f"Basic skills analysis failed for job {job_id}: {e}")
            return self._create_fallback_basic_skills_analysis(resume_text)
    
    async def _generate_optimization_suggestions(
        self, resume_text: str, job_description: str, llm: BaseLanguageModel, job_id: str
    ) -> OptimizationSuggestions:
        """Generate targeted optimization suggestions."""
        prompt = self._get_optimization_prompt()
        template = ChatPromptTemplate.from_template(prompt)
        chain = template | llm | JsonOutputParser()
        
        try:
            result = await _execute_with_circuit_breaker(
                chain,
                {"resume_text": resume_text, "job_description": job_description},
                llm.__class__.__name__,
                self.optimizer
            )
            
            return OptimizationSuggestions(
                content_improvements=result.get("content_improvements", []),
                formatting_suggestions=result.get("formatting_suggestions", []),
                keyword_optimization=result.get("keyword_optimization", []),
                ats_compatibility_score=result.get("ats_compatibility_score", 0.0),
                action_items=result.get("action_items", []),
                estimated_improvement_impact=result.get("estimated_improvement_impact", 0.0)
            )
        except Exception as e:
            self.logger.error(f"Optimization suggestions failed for job {job_id}: {e}")
            return self._create_fallback_optimization_suggestions()
    
    async def _generate_general_optimizations(
        self, resume_text: str, llm: BaseLanguageModel, job_id: str
    ) -> OptimizationSuggestions:
        """Generate general optimization suggestions without job-specific targeting."""
        prompt = self._get_general_optimization_prompt()
        template = ChatPromptTemplate.from_template(prompt)
        chain = template | llm | JsonOutputParser()
        
        try:
            result = await _execute_with_circuit_breaker(
                chain, {"resume_text": resume_text}, llm.__class__.__name__, self.optimizer
            )
            
            return OptimizationSuggestions(
                content_improvements=result.get("content_improvements", []),
                formatting_suggestions=result.get("formatting_suggestions", []),
                keyword_optimization=result.get("keyword_optimization", []),
                ats_compatibility_score=result.get("ats_compatibility_score", 0.0),
                action_items=result.get("action_items", []),
                estimated_improvement_impact=result.get("estimated_improvement_impact", 0.0)
            )
        except Exception as e:
            self.logger.error(f"General optimization suggestions failed for job {job_id}: {e}")
            return self._create_fallback_optimization_suggestions()
    
    async def _execute_streaming_analysis(
        self, structure_task, skills_task, optimization_task, 
        resume_text: str, job_id: str, start_time: float
    ) -> ComprehensiveResumeAnalysis:
        """Execute analysis with streaming updates."""
        # Execute tasks with progress updates
        structure_result = await structure_task
        if self.streaming_handler:
            await self.streaming_handler.send_progress_update(job_id, "Structure analysis completed", 0.33)
        
        skills_result = await skills_task
        if self.streaming_handler:
            await self.streaming_handler.send_progress_update(job_id, "Skills analysis completed", 0.66)
        
        optimization_result = await optimization_task
        if self.streaming_handler:
            await self.streaming_handler.send_progress_update(job_id, "Optimization analysis completed", 1.0)
        
        return self._combine_analysis_results(
            structure_result, skills_result, optimization_result,
            resume_text, start_time
        )
    
    async def _execute_batch_analysis(
        self, structure_task, skills_task, optimization_task,
        resume_text: str, job_id: str, start_time: float, cache_key: Optional[str]
    ) -> ComprehensiveResumeAnalysis:
        """Execute analysis in batch mode."""
        # Execute all tasks in parallel
        structure_result, skills_result, optimization_result = await asyncio.gather(
            structure_task, skills_task, optimization_task, return_exceptions=True
        )
        
        # Handle any exceptions
        if isinstance(structure_result, Exception):
            self.logger.error(f"Structure analysis exception: {structure_result}")
            structure_result = self._create_fallback_structure_analysis(resume_text)
        
        if isinstance(skills_result, Exception):
            self.logger.error(f"Skills analysis exception: {skills_result}")
            skills_result = self._create_fallback_skills_analysis(resume_text, "")
        
        if isinstance(optimization_result, Exception):
            self.logger.error(f"Optimization analysis exception: {optimization_result}")
            optimization_result = self._create_fallback_optimization_suggestions()
        
        analysis = self._combine_analysis_results(
            structure_result, skills_result, optimization_result,
            resume_text, start_time
        )
        
        # Cache the result
        if cache_key and self.cache:
            await self._cache_analysis_result(cache_key, analysis)
        
        return analysis
    
    def _combine_analysis_results(
        self, structure: ResumeStructureAnalysis, skills: SkillsMatchingAnalysis,
        optimization: OptimizationSuggestions, resume_text: str, start_time: float
    ) -> ComprehensiveResumeAnalysis:
        """Combine individual analysis results into comprehensive analysis."""
        # Calculate overall score
        overall_score = (
            structure.overall_structure_score * 0.3 +
            (skills.match_percentage / 100.0) * 0.4 +
            optimization.estimated_improvement_impact * 0.3
        )
        
        # Identify key strengths
        key_strengths = []
        if structure.format_score > 0.8:
            key_strengths.append("Excellent resume formatting")
        if skills.match_percentage > 75:
            key_strengths.append("Strong skills alignment with job requirements")
        if optimization.ats_compatibility_score > 0.8:
            key_strengths.append("High ATS compatibility")
        
        # Identify critical issues
        critical_issues = []
        if structure.completeness_score < 0.5:
            critical_issues.append("Resume missing essential sections")
        if skills.match_percentage < 40:
            critical_issues.append("Low skills match with job requirements")
        if optimization.ats_compatibility_score < 0.5:
            critical_issues.append("Poor ATS compatibility")
        
        processing_time = time.time() - start_time
        estimated_cost = self.optimizer.estimate_cost(resume_text, "gpt-4o") * 3  # 3 AI calls
        
        return ComprehensiveResumeAnalysis(
            structure_analysis=structure,
            skills_matching=skills,
            optimization_suggestions=optimization,
            overall_score=overall_score,
            key_strengths=key_strengths,
            critical_issues=critical_issues,
            estimated_processing_cost=estimated_cost,
            analysis_metadata={
                "processing_time_seconds": processing_time,
                "resume_word_count": len(resume_text.split()),
                "analysis_timestamp": time.time(),
                "ai_model_used": "multi-stage-analysis"
            }
        )
    
    # Prompt templates for different analysis types
    def _get_structure_analysis_prompt(self) -> str:
        return """Analyze the structure and organization of this resume. Evaluate the presence and quality of different sections.

Resume Content:
{resume_text}

Return JSON with:
- sections_present: List of sections found (contact_info, summary, experience, skills, education, projects, certifications)
- sections_missing: List of commonly expected sections that are missing
- format_score: 0.0-1.0 rating of formatting quality
- organization_score: 0.0-1.0 rating of logical organization
- completeness_score: 0.0-1.0 rating of content completeness
- overall_structure_score: 0.0-1.0 overall structure rating
- recommendations: List of specific structural improvements

JSON:"""

    def _get_skills_matching_prompt(self) -> str:
        return """Analyze skills match between the resume and job description. Extract and categorize skills, assess match level.

Resume:
{resume_text}

Job Description:
{job_description}

Return JSON with:
- extracted_skills: All skills found in resume
- matched_skills: Skills that match job requirements
- missing_skills: Required skills not present in resume
- skill_categories: {{technical: [], soft: [], domain: []}}
- match_percentage: 0-100 percentage match
- skill_level_assessment: {{skill: "beginner/intermediate/advanced"}}
- recommendations: How to improve skills alignment
- priority_skills_to_add: Top 3-5 skills to focus on

JSON:"""

    def _get_basic_skills_prompt(self) -> str:
        return """Extract and categorize all skills mentioned in this resume.

Resume:
{resume_text}

Return JSON with:
- extracted_skills: All skills found in resume
- skill_categories: {{technical: [], soft: [], domain: []}}
- skill_level_assessment: {{skill: "beginner/intermediate/advanced"}} based on context
- recommendations: General skills improvement suggestions

JSON:"""

    def _get_optimization_prompt(self) -> str:
        return """Generate specific optimization suggestions to improve this resume for the target job.

Resume:
{resume_text}

Job Description:
{job_description}

Return JSON with:
- content_improvements: [{{section: "section_name", suggestion: "specific improvement"}}]
- formatting_suggestions: List of formatting improvements
- keyword_optimization: Keywords to add or emphasize
- ats_compatibility_score: 0.0-1.0 ATS friendliness rating
- action_items: [{{priority: "high/medium/low", description: "action", impact: "expected impact"}}]
- estimated_improvement_impact: 0.0-1.0 expected improvement from following suggestions

JSON:"""

    def _get_general_optimization_prompt(self) -> str:
        return """Generate general optimization suggestions to improve this resume overall.

Resume:
{resume_text}

Return JSON with:
- content_improvements: [{{section: "section_name", suggestion: "specific improvement"}}]
- formatting_suggestions: List of formatting improvements
- keyword_optimization: General keywords to consider
- ats_compatibility_score: 0.0-1.0 ATS friendliness rating
- action_items: [{{priority: "high/medium/low", description: "action", impact: "expected impact"}}]
- estimated_improvement_impact: 0.0-1.0 expected improvement from following suggestions

JSON:"""

    # Fallback methods for error cases
    def _create_fallback_structure_analysis(self, resume_text: str) -> ResumeStructureAnalysis:
        """Create basic structure analysis when AI fails."""
        return ResumeStructureAnalysis(
            sections_present=["content"],
            sections_missing=["analysis_unavailable"],
            format_score=0.5,
            organization_score=0.5,
            completeness_score=0.5,
            overall_structure_score=0.5,
            recommendations=["AI analysis temporarily unavailable. Manual review recommended."],
            word_count=len(resume_text.split()),
            estimated_reading_time_minutes=len(resume_text.split()) / 200
        )

    def _create_fallback_skills_analysis(self, resume_text: str, job_description: str) -> SkillsMatchingAnalysis:
        """Create basic skills analysis when AI fails."""
        return SkillsMatchingAnalysis(
            extracted_skills=["analysis_unavailable"],
            matched_skills=[],
            missing_skills=["analysis_unavailable"],
            skill_categories={"technical": [], "soft": [], "domain": []},
            match_percentage=0.0,
            skill_level_assessment={},
            recommendations=["AI analysis temporarily unavailable. Manual skills review recommended."],
            priority_skills_to_add=[]
        )

    def _create_fallback_basic_skills_analysis(self, resume_text: str) -> SkillsMatchingAnalysis:
        """Create basic skills analysis for resume without job description."""
        return SkillsMatchingAnalysis(
            extracted_skills=["analysis_unavailable"],
            matched_skills=[],
            missing_skills=[],
            skill_categories={"technical": [], "soft": [], "domain": []},
            match_percentage=0.0,
            skill_level_assessment={},
            recommendations=["AI analysis temporarily unavailable. Manual skills review recommended."],
            priority_skills_to_add=[]
        )

    def _create_fallback_optimization_suggestions(self) -> OptimizationSuggestions:
        """Create basic optimization suggestions when AI fails."""
        return OptimizationSuggestions(
            content_improvements=[{"general": "AI analysis temporarily unavailable"}],
            formatting_suggestions=["Manual review recommended"],
            keyword_optimization=["Analysis unavailable"],
            ats_compatibility_score=0.5,
            action_items=[{
                "priority": "high",
                "description": "Manual resume review recommended - AI analysis unavailable",
                "impact": "Ensure professional quality"
            }],
            estimated_improvement_impact=0.0
        )

    async def _get_cached_analysis(self, cache_key: str) -> Optional[ComprehensiveResumeAnalysis]:
        """Retrieve cached analysis result."""
        if not self.cache:
            return None
        
        try:
            # This would need to be implemented in the cache system
            # For now, return None to always perform fresh analysis
            return None
        except Exception as e:
            self.logger.warning(f"Cache retrieval failed: {e}")
            return None

    async def _cache_analysis_result(self, cache_key: str, analysis: ComprehensiveResumeAnalysis) -> None:
        """Cache analysis result for future use."""
        if not self.cache:
            return
        
        try:
            # This would need to be implemented in the cache system
            # For now, we'll just log the intent to cache
            self.logger.info(f"Would cache analysis result with key: {cache_key}")
        except Exception as e:
            self.logger.warning(f"Cache storage failed: {e}")


# Factory function for creating resume analysis chain
def create_resume_analysis_chain() -> ResumeAnalysisChain:
    """Create and return a configured resume analysis chain."""
    return ResumeAnalysisChain()


# Utility functions for integration with existing system
async def analyze_resume_for_job(
    resume_text: str,
    job_description: Optional[str] = None,
    job_id: Optional[str] = None,
    user_id: Optional[str] = None,
    model_provider: str = "openai",
    enable_streaming: bool = True
) -> Dict[str, Any]:
    """
    Convenient function to analyze resume and return serializable results.
    
    This function integrates with the existing Brain service architecture
    and returns results in a format compatible with Gateway callbacks.
    """
    chain = create_resume_analysis_chain()
    
    try:
        analysis = await chain.analyze_resume_comprehensive(
            resume_text=resume_text,
            job_description=job_description,
            model_provider=model_provider,
            job_id=job_id,
            user_id=user_id,
            enable_streaming=enable_streaming,
            enable_caching=True
        )
        
        # Convert to serializable dictionary
        return {
            "status": "success",
            "analysis": asdict(analysis),
            "summary": {
                "overall_score": analysis.overall_score,
                "key_strengths": analysis.key_strengths,
                "critical_issues": analysis.critical_issues,
                "processing_cost": analysis.estimated_processing_cost
            }
        }
        
    except Exception as e:
        logging.getLogger(__name__).error(f"Resume analysis failed: {e}")
        return {
            "status": "error",
            "error": str(e),
            "message": "Resume analysis failed. Please try again or contact support."
        }